package service

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
)

const totalWeeks = 20

var icsSearchStart = time.Date(2025, 9, 1, 0, 0, 0, 0, time.UTC)
var icsSearchEnd = time.Date(2028, 8, 31, 0, 0, 0, 0, time.UTC)

type calendarEvent struct {
	title       string
	description string
	uid         string
	location    string
	url         string
	date        time.Time
	start       time.Time
	end         time.Time
	allDay      bool
}

// CalendarService reads the generated school calendar feed and derives the
// current teaching week. The parsed event list is cached for ten minutes; the
// schedule itself is recomputed per request so the week rolls over at
// midnight without waiting for the cache to expire.
type CalendarService struct {
	cfg    *config.Config
	client *http.Client

	mu      sync.RWMutex
	events  []calendarEvent
	hasData bool
	expires time.Time

	assocMu      sync.RWMutex
	assocEvents  []calendarEvent
	assocHasData bool
	assocExpires time.Time

	now func() time.Time
}

func NewCalendarService(cfg *config.Config, client *http.Client) *CalendarService {
	return &CalendarService{
		cfg:    cfg,
		client: newHTTPClient(cfg, client),
		now:    time.Now,
	}
}

func (s *CalendarService) GetSchedule(ctx context.Context) (model.CalendarSchedule, error) {
	events, err := s.loadEvents(ctx)
	if err != nil {
		return model.CalendarSchedule{}, err
	}
	return buildSchedule(events, s.now()), nil
}

// GetAssociationCalendar reads /events.ics (the association Google Calendar),
// not /school.ics. It keeps upcoming activities and the previous week so the
// mobile page can show context right after an event ends.
func (s *CalendarService) GetAssociationCalendar(ctx context.Context) (model.AssociationCalendar, error) {
	events, err := s.loadAssociationEvents(ctx)
	if err != nil {
		return model.AssociationCalendar{}, err
	}
	now := s.now().In(chinaLocation)
	from := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, chinaLocation)
	to := now.AddDate(0, 0, 180)

	out := make([]model.AssociationEvent, 0, len(events))
	for _, event := range events {
		start := event.start
		if start.IsZero() {
			start = event.date
		}
		if start.Before(from) || start.After(to) {
			continue
		}
		end := event.end
		if end.IsZero() {
			if event.allDay {
				end = start.Add(24 * time.Hour)
			} else {
				end = start.Add(time.Hour)
			}
		}
		url := event.url
		if url == "" {
			url = firstURL(event.description)
		}
		out = append(out, model.AssociationEvent{
			ID:          event.uid,
			Title:       event.title,
			Description: event.description,
			Location:    event.location,
			URL:         url,
			Start:       start.In(chinaLocation).Format(time.RFC3339),
			End:         end.In(chinaLocation).Format(time.RFC3339),
			AllDay:      event.allDay,
		})
	}
	sort.SliceStable(out, func(i, j int) bool {
		return out[i].Start < out[j].Start
	})
	return model.AssociationCalendar{
		Name:    "NBTCA 计算机协会日历",
		Updated: now.Format(time.RFC3339),
		Events:  out,
	}, nil
}

var urlPattern = regexp.MustCompile(`https?://[^\s，。；）]+`)

func firstURL(text string) string {
	return strings.TrimRight(urlPattern.FindString(text), ".,;:")
}

func (s *CalendarService) loadAssociationEvents(ctx context.Context) ([]calendarEvent, error) {
	now := s.now()
	s.assocMu.RLock()
	if s.assocHasData && now.Before(s.assocExpires) {
		events := s.assocEvents
		s.assocMu.RUnlock()
		return events, nil
	}
	lastGood := s.assocEvents
	hasLastGood := s.assocHasData
	s.assocMu.RUnlock()

	if s.cfg == nil || s.cfg.CalendarEndpoint == "" {
		if hasLastGood {
			return lastGood, nil
		}
		return nil, errors.New("CALENDAR_ENDPOINT is not configured")
	}
	body, err := doRequest(ctx, s.client, http.MethodGet, s.cfg.CalendarEndpoint+"/events.ics", nil, nil)
	if err != nil {
		if hasLastGood {
			return lastGood, nil
		}
		return nil, err
	}
	events, err := parseICS(body)
	if err != nil {
		if hasLastGood {
			return lastGood, nil
		}
		return nil, err
	}
	s.assocMu.Lock()
	s.assocEvents = events
	s.assocHasData = true
	s.assocExpires = now.Add(10 * time.Minute)
	s.assocMu.Unlock()
	return events, nil
}

func (s *CalendarService) loadEvents(ctx context.Context) ([]calendarEvent, error) {
	now := s.now()
	s.mu.RLock()
	if s.hasData && now.Before(s.expires) {
		events := s.events
		s.mu.RUnlock()
		return events, nil
	}
	lastGood := s.events
	hasLastGood := s.hasData
	s.mu.RUnlock()

	if s.cfg == nil || s.cfg.CalendarEndpoint == "" {
		if hasLastGood {
			return lastGood, nil
		}
		return nil, errors.New("CALENDAR_ENDPOINT is not configured")
	}

	body, err := doRequest(ctx, s.client, http.MethodGet, s.cfg.CalendarEndpoint+"/school.ics", nil, nil)
	if err != nil {
		if hasLastGood {
			return lastGood, nil
		}
		return nil, err
	}
	events, err := parseICS(body)
	if err != nil {
		if hasLastGood {
			return lastGood, nil
		}
		return nil, err
	}

	s.mu.Lock()
	s.events = events
	s.hasData = true
	s.expires = now.Add(10 * time.Minute)
	s.mu.Unlock()
	return events, nil
}

func buildSchedule(events []calendarEvent, now time.Time) model.CalendarSchedule {
	today := dateOnly(now)
	startYear := today.Year()
	if today.Month() < time.September {
		startYear--
	}
	semester := "秋季学期"
	if today.Month() >= time.February && today.Month() <= time.August {
		semester = "春季学期"
	}

	start := findTermStart(events, today, semester, startYear)
	if start.IsZero() {
		if semester == "秋季学期" {
			start = time.Date(startYear, time.September, 1, 0, 0, 0, 0, time.UTC)
		} else {
			start = time.Date(startYear+1, time.March, 1, 0, 0, 0, 0, time.UTC)
		}
	}

	currentWeek := int(today.Sub(start).Hours()/24)/7 + 1
	if currentWeek < 1 {
		currentWeek = 1
	}
	if currentWeek > totalWeeks {
		currentWeek = totalWeeks
	}

	windowStart := start.AddDate(0, 0, -14)
	windowEnd := start.AddDate(0, 0, totalWeeks*7)
	termEvents := make([]calendarEvent, 0, len(events))
	for _, event := range events {
		if event.date.Before(windowStart) || !event.date.Before(windowEnd) {
			continue
		}
		termEvents = append(termEvents, event)
	}

	schedule := model.CalendarSchedule{
		AcademicYear:   fmt.Sprintf("%d-%d 学年", startYear, startYear+1),
		Semester:       semester,
		CurrentWeek:    currentWeek,
		TotalWeeks:     totalWeeks,
		StartDate:      start.Format("2006-01-02"),
		WeeklyNotes:    []string{},
		Events:         []model.CalendarEvent{},
		DaysToVacation: 0,
	}

	for _, event := range events {
		category := eventCategory(event.title)
		if schedule.NextHoliday == "" && category == "holiday" && event.date.After(today) {
			schedule.NextHoliday = event.title
			schedule.DaysToVacation = int(event.date.Sub(today).Hours() / 24)
		}
	}

	upcoming := make([]calendarEvent, 0, 3)
	for _, event := range termEvents {
		if !event.date.Before(today) && len(upcoming) < 3 {
			upcoming = append(upcoming, event)
		}
	}
	if len(upcoming) == 0 && len(termEvents) > 0 {
		startIndex := len(termEvents) - 3
		if startIndex < 0 {
			startIndex = 0
		}
		upcoming = append(upcoming, termEvents[startIndex:]...)
	}
	for _, event := range upcoming {
		schedule.WeeklyNotes = append(schedule.WeeklyNotes, noteForEvent(event, start))
	}

	for _, event := range termEvents {
		week := weekForDate(event.date, start)
		schedule.Events = append(schedule.Events, model.CalendarEvent{
			Title:    event.title,
			Date:     event.date.Format("2006-01-02"),
			Week:     week,
			Category: eventCategory(event.title),
		})
	}
	return schedule
}

func findTermStart(events []calendarEvent, today time.Time, semester string, startYear int) time.Time {
	wanted := "秋季学期开始上课"
	rangeStart := time.Date(startYear, time.September, 1, 0, 0, 0, 0, time.UTC)
	rangeEnd := time.Date(startYear+1, time.September, 1, 0, 0, 0, 0, time.UTC)
	if semester == "春季学期" {
		wanted = "春季学期开始上课"
	}
	var best time.Time
	for _, event := range events {
		if strings.TrimSpace(event.title) != wanted {
			continue
		}
		if event.date.Before(rangeStart) || !event.date.Before(rangeEnd) {
			continue
		}
		if event.date.After(today) {
			continue
		}
		if event.date.After(best) {
			best = event.date
		}
	}
	return best
}

func weekForDate(date, start time.Time) int {
	week := int(date.Sub(start).Hours()/24)/7 + 1
	if week < 1 {
		week = 1
	}
	if week > totalWeeks {
		week = totalWeeks
	}
	return week
}

func noteForEvent(event calendarEvent, start time.Time) string {
	return fmt.Sprintf("%d 月 %d 日：%s（第 %d 周）", int(event.date.Month()), event.date.Day(), event.title, weekForDate(event.date, start))
}

func eventCategory(title string) string {
	switch {
	case strings.Contains(title, "放假") || strings.Contains(title, "假期") || strings.Contains(title, "寒假") || strings.Contains(title, "暑假"):
		return "holiday"
	case strings.Contains(title, "考试") || strings.Contains(title, "补考"):
		return "exam"
	default:
		return "activity"
	}
}

var chinaLocation = time.FixedZone("CST", 8*60*60)

func dateOnly(t time.Time) time.Time {
	local := t.In(chinaLocation)
	return time.Date(local.Year(), local.Month(), local.Day(), 0, 0, 0, 0, time.UTC)
}

func parseICS(data []byte) ([]calendarEvent, error) {
	lines := unfoldICS(data)
	events := make([]calendarEvent, 0, 64)
	var current *calendarEvent
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		switch strings.ToUpper(line) {
		case "BEGIN:VEVENT":
			current = &calendarEvent{}
			continue
		case "END:VEVENT":
			if current != nil && current.title != "" && !current.date.IsZero() {
				if current.date.After(icsSearchStart) && current.date.Before(icsSearchEnd) {
					events = append(events, *current)
				}
			}
			current = nil
			continue
		}
		if current == nil {
			continue
		}
		name, value := splitICSProperty(line)
		switch name {
		case "SUMMARY":
			current.title = strings.TrimSpace(value)
		case "DESCRIPTION":
			current.description = strings.TrimSpace(value)
		case "UID":
			current.uid = strings.TrimSpace(value)
		case "LOCATION":
			current.location = strings.TrimSpace(value)
		case "URL":
			current.url = strings.TrimSpace(value)
		case "DTSTART":
			if parsed, allDay, ok := parseICSDateTime(value); ok {
				current.start = parsed
				current.date = dateOnly(parsed)
				current.allDay = allDay
			} else if parsed, ok := parseICSDate(value); ok {
				current.date = parsed
			}
		case "DTEND":
			if parsed, _, ok := parseICSDateTime(value); ok {
				current.end = parsed
			} else if parsed, ok := parseICSDate(value); ok {
				current.end = parsed
			}
		}
	}

	if len(events) == 0 {
		return nil, errors.New("calendar feed contains no usable events")
	}
	sort.SliceStable(events, func(i, j int) bool {
		if events[i].date.Equal(events[j].date) {
			return events[i].title < events[j].title
		}
		return events[i].date.Before(events[j].date)
	})
	return events, nil
}

func unfoldICS(data []byte) []string {
	text := strings.ReplaceAll(string(data), "\r\n", "\n")
	text = strings.ReplaceAll(text, "\r", "\n")
	raw := strings.Split(text, "\n")
	lines := make([]string, 0, len(raw))
	for _, line := range raw {
		if len(line) > 0 && (line[0] == ' ' || line[0] == '\t') && len(lines) > 0 {
			lines[len(lines)-1] += strings.TrimLeft(line, " \t")
			continue
		}
		lines = append(lines, line)
	}
	return lines
}

func splitICSProperty(line string) (string, string) {
	index := strings.Index(line, ":")
	if index < 0 {
		return "", ""
	}
	head := line[:index]
	name := strings.ToUpper(strings.TrimSpace(strings.SplitN(head, ";", 2)[0]))
	value := unescapeICS(line[index+1:])
	return name, value
}

// parseICSDateTime preserves the event time while retaining the school
// calendar's date-only behavior. Google/Apple feeds mix UTC timestamps,
// TZID-qualified local times, floating local times and all-day dates.
func parseICSDateTime(value string) (time.Time, bool, bool) {
	value = strings.TrimSpace(strings.Trim(value, "\""))
	if len(value) >= 8 && allDigits(value[:8]) {
		year, _ := strconv.Atoi(value[0:4])
		month, _ := strconv.Atoi(value[4:6])
		day, _ := strconv.Atoi(value[6:8])
		if month < 1 || month > 12 || day < 1 || day > 31 {
			return time.Time{}, false, false
		}
		if len(value) == 8 {
			return time.Date(year, time.Month(month), day, 0, 0, 0, 0, chinaLocation), true, true
		}
		if len(value) >= 15 && (value[8] == 'T' || value[8] == 't') {
			hour, _ := strconv.Atoi(value[9:11])
			minute, _ := strconv.Atoi(value[11:13])
			second := 0
			if len(value) >= 15 {
				second, _ = strconv.Atoi(value[13:15])
			}
			if strings.HasSuffix(value, "Z") {
				utc := time.Date(year, time.Month(month), day, hour, minute, second, 0, time.UTC)
				return utc.In(chinaLocation), false, true
			}
			if len(value) >= 19 {
				if parsed, err := time.Parse("20060102T150405-0700", value); err == nil {
					return parsed.In(chinaLocation), false, true
				}
			}
			return time.Date(year, time.Month(month), day, hour, minute, second, 0, chinaLocation), false, true
		}
	}
	for _, layout := range []string{time.RFC3339, "2006-01-02T15:04:05Z07:00"} {
		if parsed, err := time.Parse(layout, value); err == nil {
			return parsed.In(chinaLocation), false, true
		}
	}
	return time.Time{}, false, false
}

func parseICSDate(value string) (time.Time, bool) {
	value = strings.TrimSpace(strings.Trim(value, `"`))
	if len(value) >= 8 && allDigits(value[:8]) {
		year, _ := strconv.Atoi(value[0:4])
		month, _ := strconv.Atoi(value[4:6])
		day, _ := strconv.Atoi(value[6:8])
		if month >= 1 && month <= 12 && day >= 1 && day <= 31 {
			return time.Date(year, time.Month(month), day, 0, 0, 0, 0, time.UTC), true
		}
	}
	for _, layout := range []string{"2006-01-02", time.RFC3339, "20060102T150405Z", "20060102T150405"} {
		if parsed, err := time.Parse(layout, value); err == nil {
			return time.Date(parsed.Year(), parsed.Month(), parsed.Day(), 0, 0, 0, 0, time.UTC), true
		}
	}
	return time.Time{}, false
}

func allDigits(value string) bool {
	for _, r := range value {
		if r < '0' || r > '9' {
			return false
		}
	}
	return true
}

func unescapeICS(value string) string {
	replacer := strings.NewReplacer(
		`\n`, "\n",
		`\N`, "\n",
		`\,`, ",",
		`\;`, ";",
		`\\`, "\\",
	)
	return replacer.Replace(value)
}
