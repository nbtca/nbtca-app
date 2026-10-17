package service

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"space.nbtca/backend/internal/config"
)

func TestCalendarScheduleForOctober17(t *testing.T) {
	var hits int32
	mux := http.NewServeMux()
	mux.HandleFunc("/school.ics", func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		w.Header().Set("Content-Type", "text/calendar; charset=utf-8")
		fmt.Fprint(w, `BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:start-autumn
DTSTART;VALUE=DATE:20260914
SUMMARY:秋季学期开始上课
DESCRIPTION:第一周
END:VEVENT
BEGIN:VEVENT
UID:national
DTSTART;VALUE=DATE:20261001
DTEND;VALUE=DATE:20261008
SUMMARY:国庆节放假
END:VEVENT
BEGIN:VEVENT
UID:sports
DTSTART:20261029T133000+0800
DTEND:20261029T170000+0800
SUMMARY:校运动会
END:VEVENT
BEGIN:VEVENT
UID:newyear
DTSTART;VALUE=DATE:20270101
SUMMARY:元旦
 放假
END:VEVENT
BEGIN:VEVENT
UID:exam
DTSTART;VALUE=DATE:20270113
SUMMARY:期末考试
END:VEVENT
BEGIN:VEVENT
UID:outside-old
DTSTART;VALUE=DATE:20250901
SUMMARY:边界旧事件
END:VEVENT
BEGIN:VEVENT
UID:outside-new
DTSTART;VALUE=DATE:20280831
SUMMARY:边界新事件
END:VEVENT
END:VCALENDAR
`)
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	cfg := testConfig()
	cfg.CalendarEndpoint = server.URL
	svc := NewCalendarService(cfg, &http.Client{Timeout: 10 * time.Second})
	svc.now = func() time.Time {
		return time.Date(2026, 10, 17, 12, 0, 0, 0, time.UTC)
	}

	schedule, err := svc.GetSchedule(context.Background())
	if err != nil {
		t.Fatalf("GetSchedule: %v", err)
	}
	if schedule.AcademicYear != "2026-2027 学年" {
		t.Errorf("academic year = %q", schedule.AcademicYear)
	}
	if schedule.Semester != "秋季学期" {
		t.Errorf("semester = %q", schedule.Semester)
	}
	if schedule.CurrentWeek != 5 {
		t.Errorf("current week = %d, want 5", schedule.CurrentWeek)
	}
	if schedule.TotalWeeks != 20 {
		t.Errorf("total weeks = %d", schedule.TotalWeeks)
	}
	if schedule.StartDate != "2026-09-14" {
		t.Errorf("start date = %q", schedule.StartDate)
	}
	if schedule.NextHoliday != "元旦放假" {
		t.Errorf("next holiday = %q", schedule.NextHoliday)
	}
	if schedule.DaysToVacation != 76 {
		t.Errorf("days to vacation = %d, want 76", schedule.DaysToVacation)
	}
	if len(schedule.WeeklyNotes) != 3 || !strings.Contains(strings.Join(schedule.WeeklyNotes, "|"), "校运动会") {
		t.Errorf("weekly notes = %v", schedule.WeeklyNotes)
	}
	if len(schedule.Events) != 5 {
		t.Fatalf("events = %d, want 5: %+v", len(schedule.Events), schedule.Events)
	}
	if schedule.Events[0].Title != "秋季学期开始上课" || schedule.Events[0].Week != 1 {
		t.Errorf("first event = %+v", schedule.Events[0])
	}
	foundExam := false
	foundHoliday := false
	for _, event := range schedule.Events {
		if event.Title == "期末考试" && event.Category == "exam" {
			foundExam = true
		}
		if event.Title == "国庆节放假" && event.Category == "holiday" {
			foundHoliday = true
		}
	}
	if !foundExam || !foundHoliday {
		t.Errorf("categories missing: %+v", schedule.Events)
	}

	if _, err := svc.GetSchedule(context.Background()); err != nil {
		t.Fatalf("second GetSchedule: %v", err)
	}
	if got := atomic.LoadInt32(&hits); got != 1 {
		t.Errorf("ICS hits = %d, want 1 (cache)", got)
	}
}

func TestAssociationCalendarUsesEventsFeed(t *testing.T) {
	var hits int32
	mux := http.NewServeMux()
	mux.HandleFunc("/events.ics", func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		w.Header().Set("Content-Type", "text/calendar; charset=utf-8")
		fmt.Fprint(w, "BEGIN:VCALENDAR\nVERSION:2.0\n"+
			"BEGIN:VEVENT\nUID:activity-1\nDTSTART;TZID=Asia/Shanghai:20261018T143000\nDTEND;TZID=Asia/Shanghai:20261018T160000\nSUMMARY:迎新大会\nLOCATION:NB101\nURL:https://nbtca.space/events/1\nDESCRIPTION:欢迎新同学\nEND:VEVENT\n"+
			"BEGIN:VEVENT\nUID:old\nDTSTART;VALUE=DATE:20250101\nSUMMARY:旧活动\nEND:VEVENT\n"+
			"BEGIN:VEVENT\nUID:all-day\nDTSTART;VALUE=DATE:20270101\nDTEND;VALUE=DATE:20270102\nSUMMARY:元旦活动\nEND:VEVENT\n"+
			"END:VCALENDAR\n")
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	cfg := testConfig()
	cfg.CalendarEndpoint = server.URL
	svc := NewCalendarService(cfg, &http.Client{Timeout: 10 * time.Second})
	svc.now = func() time.Time {
		return time.Date(2026, 10, 17, 6, 0, 0, 0, time.UTC)
	}
	calendar, err := svc.GetAssociationCalendar(context.Background())
	if err != nil {
		t.Fatalf("GetAssociationCalendar: %v", err)
	}
	if len(calendar.Events) != 2 {
		t.Fatalf("events = %+v, want 2 upcoming", calendar.Events)
	}
	first := calendar.Events[0]
	if first.Title != "迎新大会" || first.Start != "2026-10-18T14:30:00+08:00" {
		t.Errorf("first event = %+v", first)
	}
	if first.Location != "NB101" || first.URL != "https://nbtca.space/events/1" {
		t.Errorf("event metadata = %+v", first)
	}
	if calendar.Events[1].Title != "元旦活动" || !calendar.Events[1].AllDay {
		t.Errorf("all-day event = %+v", calendar.Events[1])
	}
	if hits != 1 {
		t.Errorf("events.ics hits = %d, want 1", hits)
	}
	if _, err := svc.GetAssociationCalendar(context.Background()); err != nil {
		t.Fatalf("cached GetAssociationCalendar: %v", err)
	}
	if hits != 1 {
		t.Errorf("events.ics hits after cache = %d, want 1", hits)
	}
}

func TestParseICSUnfoldsAndParsesDates(t *testing.T) {
	data := []byte("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:x\r\nDTSTART;VALUE=DATE:20260914\r\nSUMMARY:秋季学期\r\n 开始上课\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")
	events, err := parseICS(data)
	if err != nil {
		t.Fatalf("parseICS: %v", err)
	}
	if len(events) != 1 || events[0].title != "秋季学期开始上课" {
		t.Fatalf("events = %+v", events)
	}
	if events[0].date.Format("2006-01-02") != "2026-09-14" {
		t.Fatalf("date = %s", events[0].date)
	}
}

func TestCalendarServiceRejectsEmptyFeed(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, "BEGIN:VCALENDAR\nEND:VCALENDAR\n")
	}))
	defer server.Close()

	cfg := &config.Config{HTTPTimeout: 10 * time.Second, CalendarEndpoint: server.URL}
	svc := NewCalendarService(cfg, nil)
	if _, err := svc.GetSchedule(context.Background()); err == nil {
		t.Fatal("expected error for empty calendar feed")
	}
}
