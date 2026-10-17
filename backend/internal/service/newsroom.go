package service

import (
	"context"
	"encoding/xml"
	"errors"
	"fmt"
	stdhtml "html"
	"net/http"
	"net/url"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
)

var ErrArticleNotFound = errors.New("article not found")

var tagPattern = regexp.MustCompile(`(?s)<[^>]*>`)

type newsItem struct {
	article    model.NewsArticle
	link       string
	categories []string
	published  time.Time
}

type newsFeedCache struct {
	items []newsItem
	feed  model.NewsFeedResponse
}

type cachedArticle struct {
	article model.NewsArticle
	expires time.Time
}

// NewsroomService aggregates the public nbtca.space RSS feed and turns post
// pages into Markdown. Both the feed and individual article bodies are cached
// in memory for five minutes.
type NewsroomService struct {
	cfg         *config.Config
	client      *http.Client
	feedCache   *ttlCache[newsFeedCache]
	articleMu   sync.RWMutex
	articleMemo map[string]cachedArticle
	now         func() time.Time
}

func NewNewsroomService(cfg *config.Config, client *http.Client) *NewsroomService {
	now := time.Now
	return &NewsroomService{
		cfg:         cfg,
		client:      newHTTPClient(cfg, client),
		feedCache:   newTTLCache[newsFeedCache](5*time.Minute, now),
		articleMemo: make(map[string]cachedArticle),
		now:         now,
	}
}

func (s *NewsroomService) GetFeed(ctx context.Context) (model.NewsFeedResponse, error) {
	if cached, ok := s.feedCache.get(); ok {
		return cached.feed, nil
	}
	items, err := s.fetchItems(ctx)
	if err != nil {
		return emptyFeed(), err
	}
	s.enrichCovers(ctx, items, 14)
	cache := newsFeedCache{items: items, feed: buildFeed(items)}
	s.feedCache.set(cache)
	return cache.feed, nil
}

func (s *NewsroomService) GetArticle(ctx context.Context, id string) (*model.NewsArticle, error) {
	cache, ok := s.feedCache.get()
	if !ok {
		items, err := s.fetchItems(ctx)
		if err != nil {
			return nil, err
		}
		cache = newsFeedCache{items: items, feed: buildFeed(items)}
		s.feedCache.set(cache)
	}

	var selected *newsItem
	for i := range cache.items {
		if cache.items[i].article.ID == id {
			selected = &cache.items[i]
			break
		}
	}
	if selected == nil {
		return nil, ErrArticleNotFound
	}

	if cached, ok := s.getCachedArticle(id); ok {
		return &cached, nil
	}

	article := selected.article
	if article.ArticleURL != "" {
		body, err := doRequest(ctx, s.client, http.MethodGet, article.ArticleURL, nil, nil)
		if err == nil {
			cover, markdown := extractArticle(body, article.ArticleURL)
			if cover != "" {
				article.CoverURL = cover
			}
			if strings.TrimSpace(markdown) != "" {
				article.ContentMarkdown = markdown
			}
		}
	}
	if strings.TrimSpace(article.ContentMarkdown) == "" {
		article.ContentMarkdown = fallbackMarkdown(article)
	}

	s.setCachedArticle(id, article)
	return &article, nil
}

func (s *NewsroomService) fetchItems(ctx context.Context) ([]newsItem, error) {
	if s.cfg == nil || s.cfg.SpaceSiteEndpoint == "" {
		return nil, errors.New("SPACE_SITE_ENDPOINT is not configured")
	}
	rawURL := s.cfg.SpaceSiteEndpoint + "/rss.xml"
	body, err := doRequest(ctx, s.client, http.MethodGet, rawURL, nil, nil)
	if err != nil {
		return nil, err
	}
	items, err := parseRSS(body, s.cfg.SpaceSiteEndpoint)
	if err != nil {
		return nil, err
	}
	return items, nil
}

func (s *NewsroomService) getCachedArticle(id string) (model.NewsArticle, bool) {
	s.articleMu.RLock()
	defer s.articleMu.RUnlock()
	cached, ok := s.articleMemo[id]
	if !ok || s.now().After(cached.expires) {
		return model.NewsArticle{}, false
	}
	return cached.article, true
}

func (s *NewsroomService) setCachedArticle(id string, article model.NewsArticle) {
	s.articleMu.Lock()
	defer s.articleMu.Unlock()
	s.articleMemo[id] = cachedArticle{article: article, expires: s.now().Add(5 * time.Minute)}
}

// enrichCovers pulls the article page for the first N feed items and stores its
// og:image/正文 in the article cache. RSS itself carries no cover image, so
// without this the mobile list can only render the category placeholder.
//
// Requests are bounded to four concurrent fetches and never fail the feed: a
// failed page simply keeps the placeholder.
func (s *NewsroomService) enrichCovers(ctx context.Context, items []newsItem, limit int) {
	if limit > len(items) {
		limit = len(items)
	}
	sem := make(chan struct{}, 6)
	var wg sync.WaitGroup
	for i := 0; i < limit; i++ {
		if cached, ok := s.getCachedArticle(items[i].article.ID); ok {
			items[i].article.CoverURL = cached.CoverURL
			if cached.ContentMarkdown != "" {
				items[i].article.ContentMarkdown = cached.ContentMarkdown
			}
			continue
		}
		if items[i].article.ArticleURL == "" {
			continue
		}
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			article := items[idx].article
			// 封面只是列表增强，不能让单个慢页面拖住整个 feed。
			coverCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
			defer cancel()
			body, err := doRequest(coverCtx, s.client, http.MethodGet, article.ArticleURL, nil, nil)
			if err != nil {
				return
			}
			cover, markdown := extractArticle(body, article.ArticleURL)
			if cover == "" && markdown == "" {
				return
			}
			article.CoverURL = cover
			article.ContentMarkdown = markdown
			items[idx].article = article
			s.setCachedArticle(article.ID, article)
		}(i)
	}
	wg.Wait()
}

type rssDocument struct {
	Channel rssChannel `xml:"channel"`
}

type rssChannel struct {
	Items []rssItem `xml:"item"`
}

type rssItem struct {
	Title       string   `xml:"title"`
	Link        string   `xml:"link"`
	Description string   `xml:"description"`
	Author      string   `xml:"author"`
	Creator     string   `xml:"creator"`
	Categories  []string `xml:"category"`
	PubDate     string   `xml:"pubDate"`
	GUID        string   `xml:"guid"`
}

func parseRSS(data []byte, siteEndpoint string) ([]newsItem, error) {
	var doc rssDocument
	if err := xml.Unmarshal(data, &doc); err != nil {
		return nil, fmt.Errorf("parse RSS: %w", err)
	}
	items := make([]newsItem, 0, len(doc.Channel.Items))
	for _, raw := range doc.Channel.Items {
		link := strings.TrimSpace(raw.Link)
		if link == "" {
			link = strings.TrimSpace(raw.GUID)
		}
		link = absoluteURL(siteEndpoint, link)
		id := extractArticleID(link)
		if id == "" {
			id = extractArticleID(raw.GUID)
		}
		if id == "" {
			continue
		}
		published, _ := parsePubDate(raw.PubDate)
		author := strings.TrimSpace(raw.Author)
		if author == "" {
			author = strings.TrimSpace(raw.Creator)
		}
		if author == "" {
			author = "NBTCA"
		}
		categories := cleanStrings(raw.Categories)
		category := "资讯"
		if len(categories) > 0 {
			category = categories[0]
		}
		title := strings.TrimSpace(stdhtml.UnescapeString(raw.Title))
		items = append(items, newsItem{
			article: model.NewsArticle{
				ID:         id,
				Title:      title,
				Category:   category,
				Published:  formatPublished(published, raw.PubDate),
				Author:     author,
				Summary:    stripHTML(raw.Description),
				ArticleURL: link,
				Tags:       categories,
			},
			link:       link,
			categories: categories,
			published:  published,
		})
	}

	sort.SliceStable(items, func(i, j int) bool {
		if items[i].published.IsZero() || items[j].published.IsZero() {
			return !items[i].published.IsZero() && items[j].published.IsZero()
		}
		return items[i].published.After(items[j].published)
	})
	return items, nil
}

func buildFeed(items []newsItem) model.NewsFeedResponse {
	feed := emptyFeed()
	if len(items) == 0 {
		return feed
	}
	for i := range items {
		feed.Categories = appendUnique(feed.Categories, items[i].article.Category)
	}
	featuredCount := 2
	if len(items) < featuredCount {
		featuredCount = len(items)
	}
	for i := 0; i < featuredCount; i++ {
		feed.Featured = append(feed.Featured, items[i].article)
	}
	recentEnd := len(items)
	if recentEnd > featuredCount+12 {
		recentEnd = featuredCount + 12
	}
	for i := featuredCount; i < recentEnd; i++ {
		feed.Recent = append(feed.Recent, items[i].article)
	}
	return feed
}

func emptyFeed() model.NewsFeedResponse {
	return model.NewsFeedResponse{
		Featured:   []model.NewsArticle{},
		Recent:     []model.NewsArticle{},
		Categories: []string{},
	}
}

func fallbackMarkdown(article model.NewsArticle) string {
	var b strings.Builder
	if article.Title != "" {
		b.WriteString("## ")
		b.WriteString(article.Title)
		b.WriteString("\n\n")
	}
	if article.Summary != "" {
		b.WriteString(article.Summary)
		b.WriteString("\n\n")
	}
	if article.ArticleURL != "" {
		b.WriteString("> 正文抓取失败，请访问 [原文链接](")
		b.WriteString(article.ArticleURL)
		b.WriteString(") 阅读完整内容。\n")
	}
	if strings.TrimSpace(b.String()) == "" {
		return "正文暂时无法获取，请稍后重试。"
	}
	return strings.TrimSpace(b.String())
}

func extractArticleID(link string) string {
	if link == "" {
		return ""
	}
	path := link
	if parsed, err := url.Parse(link); err == nil && parsed.Path != "" {
		path = parsed.Path
	}
	idx := strings.Index(path, "/posts/")
	if idx >= 0 {
		path = path[idx+len("/posts/"):]
	} else {
		path = strings.TrimPrefix(path, "/")
	}
	path = strings.Trim(path, "/")
	return path
}

func absoluteURL(base, ref string) string {
	ref = strings.TrimSpace(ref)
	if ref == "" {
		return ""
	}
	parsed, err := url.Parse(ref)
	if err == nil && parsed.IsAbs() {
		return ref
	}
	baseURL, err := url.Parse(strings.TrimRight(base, "/") + "/")
	if err != nil {
		return ref
	}
	resolved, err := baseURL.Parse(ref)
	if err != nil {
		return ref
	}
	return resolved.String()
}

func parsePubDate(raw string) (time.Time, bool) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return time.Time{}, false
	}
	layouts := []string{
		time.RFC1123Z,
		time.RFC1123,
		time.RFC822Z,
		time.RFC822,
		"Mon, 02 Jan 2006 15:04:05 -0700",
		"2006-01-02T15:04:05Z07:00",
		"2006-01-02 15:04:05",
		"2006-01-02",
	}
	for _, layout := range layouts {
		if t, err := time.Parse(layout, raw); err == nil {
			return t, true
		}
	}
	return time.Time{}, false
}

func formatPublished(t time.Time, raw string) string {
	if t.IsZero() {
		return strings.TrimSpace(raw)
	}
	return fmt.Sprintf("%d 年 %d 月 %d 日", t.Year(), int(t.Month()), t.Day())
}

func stripHTML(raw string) string {
	raw = stdhtml.UnescapeString(raw)
	return strings.Join(strings.Fields(tagPattern.ReplaceAllString(raw, " ")), " ")
}

func cleanStrings(values []string) []string {
	out := make([]string, 0, len(values))
	for _, value := range values {
		value = strings.TrimSpace(stdhtml.UnescapeString(value))
		if value != "" {
			out = append(out, value)
		}
	}
	return out
}

func appendUnique(dst []string, values ...string) []string {
	for _, value := range values {
		found := false
		for _, existing := range dst {
			if existing == value {
				found = true
				break
			}
		}
		if !found {
			dst = append(dst, value)
		}
	}
	return dst
}
