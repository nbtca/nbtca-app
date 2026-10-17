package model

import "encoding/json"

type ApiResponse struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

type NewsArticle struct {
	ID              string   `json:"id"`
	Title           string   `json:"title"`
	Category        string   `json:"category"`
	Published       string   `json:"published"`
	Author          string   `json:"author"`
	Summary         string   `json:"summary"`
	CoverURL        string   `json:"cover_url"`
	ArticleURL      string   `json:"article_url"`
	ContentMarkdown string   `json:"content_markdown,omitempty"`
	Tags            []string `json:"tags"`
}

type NewsFeedResponse struct {
	Featured   []NewsArticle `json:"featured"`
	Recent     []NewsArticle `json:"recent"`
	Categories []string      `json:"categories"`
}

type DiagnosisRequest struct {
	DeviceType  string `json:"device_type"`
	Description string `json:"description"`
}

type DiagnosisResponse struct {
	DeviceType       string   `json:"device_type"`
	PossibleCauses   []string `json:"possible_causes"`
	SelfHelpSteps    []string `json:"self_help_steps"`
	RecommendedTools []string `json:"recommended_tools"`
	SuggestRepair    bool     `json:"suggest_repair"`
	Summary          string   `json:"summary"`
	Source           string   `json:"source"` // dify or heuristic
}

type CalendarSchedule struct {
	AcademicYear   string          `json:"academic_year"`
	Semester       string          `json:"semester"`
	CurrentWeek    int             `json:"current_week"`
	TotalWeeks     int             `json:"total_weeks"`
	StartDate      string          `json:"start_date"`
	NextHoliday    string          `json:"next_holiday"`
	DaysToVacation int             `json:"days_to_vacation"`
	WeeklyNotes    []string        `json:"weekly_notes"`
	Events         []CalendarEvent `json:"events"`
}

type CalendarEvent struct {
	Title    string `json:"title"`
	Date     string `json:"date"`
	Week     int    `json:"week"`
	Category string `json:"category"` // exam, holiday, activity
}

// AssociationCalendar is the public activities feed from ical.nbtca.space/events.ics.
type AssociationCalendar struct {
	Name    string             `json:"name"`
	Updated string             `json:"updated"`
	Events  []AssociationEvent `json:"events"`
}

type AssociationEvent struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Location    string `json:"location"`
	URL         string `json:"url"`
	Start       string `json:"start"` // RFC3339 in Asia/Shanghai
	End         string `json:"end"`
	AllDay      bool   `json:"all_day"`
}

// StatusResponse mirrors https://status.nbtca.space/api/status. Incidents stay
// as raw JSON so the BFF forwards future fields without a code change.
type StatusResponse struct {
	State     string            `json:"state"`
	Updated   int64             `json:"updated"`
	Groups    []StatusGroup     `json:"groups"`
	Incidents []json.RawMessage `json:"incidents"`
}

type StatusGroup struct {
	Name       string            `json:"name"`
	Infra      bool              `json:"infra"`
	Components []StatusComponent `json:"components"`
}

type StatusComponent struct {
	ID     string   `json:"id"`
	Name   string   `json:"name"`
	State  string   `json:"state"`
	Since  int64    `json:"since"`
	Failed []string `json:"failed"`
	Error  *string  `json:"error"`
}

type UserProfile struct {
	Sub      string   `json:"sub"`
	Name     string   `json:"name"`
	Username string   `json:"username"`
	Email    string   `json:"email"`
	Picture  string   `json:"picture"`
	Roles    []string `json:"roles"`
}

type SaturdayStatus struct {
	Endpoint  string `json:"endpoint"`
	Reachable bool   `json:"reachable"`
	LatencyMS int64  `json:"latency_ms"`
	Message   string `json:"message"`
}

type AppVersionInfo struct {
	LatestVersion string `json:"latest_version"`
	VersionCode   int    `json:"version_code"`
	DownloadURL   string `json:"download_url"`
	Changelog     string `json:"changelog"`
	ForceUpdate   bool   `json:"force_update"`
	ReleaseDate   string `json:"release_date"`
}
