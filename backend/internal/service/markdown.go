package service

import (
	"bytes"
	"fmt"
	"net/url"
	"regexp"
	"strings"
	"unicode"
	"unicode/utf8"

	xhtml "golang.org/x/net/html"
)

var trailingSpacePattern = regexp.MustCompile(`[ \t]+\n`)

// extractArticle returns the Open Graph cover image and a Markdown rendering
// of the main article body.
func extractArticle(data []byte, baseURL string) (string, string) {
	doc, err := xhtml.Parse(bytes.NewReader(data))
	if err != nil {
		return "", ""
	}
	cover := findCoverImage(doc, baseURL)
	container := findContentContainer(doc)
	if container == nil {
		return cover, ""
	}
	if cover == "" {
		cover = findFirstImage(container, baseURL)
	}
	renderer := newMarkdownRenderer(baseURL)
	renderer.renderChildren(container)
	markdown := cleanupMarkdown(renderer.String())
	return cover, markdown
}

func findContentContainer(doc *xhtml.Node) *xhtml.Node {
	for _, tag := range []string{"article", "main"} {
		if node := findElement(doc, tag); node != nil {
			return node
		}
	}
	return findElement(doc, "body")
}

func findElement(node *xhtml.Node, tag string) *xhtml.Node {
	if node.Type == xhtml.ElementNode && strings.EqualFold(node.Data, tag) {
		return node
	}
	for child := node.FirstChild; child != nil; child = child.NextSibling {
		if result := findElement(child, tag); result != nil {
			return result
		}
	}
	return nil
}

func findCoverImage(doc *xhtml.Node, baseURL string) string {
	var walk func(*xhtml.Node) string
	walk = func(node *xhtml.Node) string {
		if node.Type == xhtml.ElementNode {
			tag := strings.ToLower(node.Data)
			if tag == "meta" {
				name := strings.ToLower(attribute(node, "property"))
				if name == "" {
					name = strings.ToLower(attribute(node, "name"))
				}
				if name == "og:image" || name == "og:image:url" || name == "twitter:image" {
					if content := strings.TrimSpace(attribute(node, "content")); content != "" {
						return resolveURL(baseURL, content)
					}
				}
			}
			if tag == "link" && strings.EqualFold(attribute(node, "rel"), "image_src") {
				if href := strings.TrimSpace(attribute(node, "href")); href != "" {
					return resolveURL(baseURL, href)
				}
			}
		}
		for child := node.FirstChild; child != nil; child = child.NextSibling {
			if result := walk(child); result != "" {
				return result
			}
		}
		return ""
	}
	return walk(doc)
}

func findFirstImage(node *xhtml.Node, baseURL string) string {
	if node.Type == xhtml.ElementNode && strings.EqualFold(node.Data, "img") {
		if src := strings.TrimSpace(attribute(node, "src")); src != "" {
			return resolveURL(baseURL, src)
		}
	}
	for child := node.FirstChild; child != nil; child = child.NextSibling {
		if result := findFirstImage(child, baseURL); result != "" {
			return result
		}
	}
	return ""
}

type markdownRenderer struct {
	builder    strings.Builder
	base       *url.URL
	lastByte   byte
	trailingNL int
}

func newMarkdownRenderer(baseURL string) *markdownRenderer {
	parsed, _ := url.Parse(baseURL)
	return &markdownRenderer{base: parsed}
}

func (r *markdownRenderer) String() string {
	return r.builder.String()
}

func (r *markdownRenderer) write(s string) {
	if s == "" {
		return
	}
	r.builder.WriteString(s)
	r.lastByte = s[len(s)-1]
	if r.lastByte == '\n' {
		count := 0
		for i := len(s) - 1; i >= 0 && s[i] == '\n'; i-- {
			count++
		}
		if count == len(s) {
			r.trailingNL += count
		} else {
			r.trailingNL = count
		}
	} else {
		r.trailingNL = 0
	}
}

func (r *markdownRenderer) ensureNewlines(count int) {
	for r.trailingNL < count {
		r.write("\n")
	}
}

func (r *markdownRenderer) ensureSpace() {
	if r.builder.Len() == 0 {
		return
	}
	if r.lastByte == ' ' || r.lastByte == '\n' || r.lastByte == '\t' {
		return
	}
	r.write(" ")
}

func (r *markdownRenderer) renderChildren(node *xhtml.Node) {
	for child := node.FirstChild; child != nil; child = child.NextSibling {
		r.renderNode(child)
	}
}

func (r *markdownRenderer) renderNode(node *xhtml.Node) {
	switch node.Type {
	case xhtml.TextNode:
		r.renderText(node)
		return
	case xhtml.ElementNode:
	default:
		return
	}

	tag := strings.ToLower(node.Data)
	switch tag {
	case "script", "style", "nav", "header", "footer", "noscript", "svg", "form", "button":
		return
	case "h1", "h2", "h3", "h4":
		level := int(tag[1] - '0')
		r.ensureNewlines(2)
		r.write(strings.Repeat("#", level))
		r.write(" ")
		r.renderChildren(node)
		r.ensureNewlines(2)
	case "p":
		r.ensureNewlines(2)
		r.renderChildren(node)
		r.ensureNewlines(2)
	case "br":
		r.write("  \n")
	case "strong", "b":
		r.write("**")
		r.renderChildren(node)
		r.write("**")
	case "em", "i":
		r.write("*")
		r.renderChildren(node)
		r.write("*")
	case "code":
		r.renderInlineCode(node)
	case "pre":
		r.renderCodeBlock(node)
	case "blockquote":
		r.renderBlockquote(node)
	case "ul", "ol":
		r.renderList(node, tag == "ol", 0)
	case "img":
		r.renderImage(node)
	case "a":
		r.renderLink(node)
	case "hr":
		r.ensureNewlines(2)
		r.write("---")
		r.ensureNewlines(2)
	case "figure", "figcaption", "table", "thead", "tbody", "tr", "td", "th":
		r.ensureNewlines(2)
		r.renderChildren(node)
		r.ensureNewlines(2)
	default:
		r.renderChildren(node)
	}
}

func (r *markdownRenderer) renderText(node *xhtml.Node) {
	raw := node.Data
	if strings.TrimSpace(raw) == "" {
		return
	}
	core := strings.Join(strings.Fields(raw), " ")
	if core == "" {
		return
	}
	if startsWithSpace(raw) {
		r.ensureSpace()
	}
	r.write(core)
	if endsWithSpace(raw) {
		r.write(" ")
	}
}

func (r *markdownRenderer) renderInlineCode(node *xhtml.Node) {
	text := strings.TrimSpace(extractText(node))
	if text == "" {
		return
	}
	r.write("`")
	r.write(text)
	r.write("`")
}

func (r *markdownRenderer) renderCodeBlock(node *xhtml.Node) {
	r.ensureNewlines(2)
	language := ""
	if code := findElement(node, "code"); code != nil {
		for _, class := range strings.Fields(attribute(code, "class")) {
			if strings.HasPrefix(class, "language-") {
				language = strings.TrimPrefix(class, "language-")
			} else if strings.HasPrefix(class, "lang-") {
				language = strings.TrimPrefix(class, "lang-")
			}
		}
	}
	text := strings.Trim(extractText(node), "\n")
	r.write("```" + language + "\n")
	r.write(text)
	r.ensureNewlines(1)
	r.write("```")
	r.ensureNewlines(2)
}

func (r *markdownRenderer) renderBlockquote(node *xhtml.Node) {
	inner := newMarkdownRenderer(r.base.String())
	inner.renderChildren(node)
	text := strings.TrimSpace(inner.String())
	if text == "" {
		return
	}
	r.ensureNewlines(2)
	for _, line := range strings.Split(text, "\n") {
		if strings.TrimSpace(line) == "" {
			r.write(">\n")
			continue
		}
		r.write("> ")
		r.write(line)
		r.write("\n")
	}
	r.ensureNewlines(2)
}

func (r *markdownRenderer) renderList(node *xhtml.Node, ordered bool, depth int) {
	r.ensureNewlines(2)
	index := 0
	for child := node.FirstChild; child != nil; child = child.NextSibling {
		if child.Type != xhtml.ElementNode || !strings.EqualFold(child.Data, "li") {
			continue
		}
		index++
		r.ensureNewlines(1)
		r.write(strings.Repeat("  ", depth))
		if ordered {
			r.write(fmt.Sprintf("%d. ", index))
		} else {
			r.write("- ")
		}
		for itemChild := child.FirstChild; itemChild != nil; itemChild = itemChild.NextSibling {
			if itemChild.Type == xhtml.ElementNode && isListTag(itemChild.Data) {
				continue
			}
			r.renderNode(itemChild)
		}
		r.ensureNewlines(1)
		for itemChild := child.FirstChild; itemChild != nil; itemChild = itemChild.NextSibling {
			if itemChild.Type == xhtml.ElementNode && isListTag(itemChild.Data) {
				r.renderList(itemChild, strings.EqualFold(itemChild.Data, "ol"), depth+1)
			}
		}
	}
	r.ensureNewlines(2)
}

func isListTag(tag string) bool {
	tag = strings.ToLower(tag)
	return tag == "ul" || tag == "ol"
}

func (r *markdownRenderer) renderImage(node *xhtml.Node) {
	src := strings.TrimSpace(attribute(node, "src"))
	if src == "" {
		src = strings.TrimSpace(attribute(node, "data-src"))
	}
	if src == "" {
		return
	}
	alt := strings.TrimSpace(attribute(node, "alt"))
	r.write("![")
	r.write(alt)
	r.write("](")
	r.write(resolveURL(r.base.String(), src))
	r.write(")")
}

func (r *markdownRenderer) renderLink(node *xhtml.Node) {
	href := strings.TrimSpace(attribute(node, "href"))
	if href == "" {
		r.renderChildren(node)
		return
	}
	r.write("[")
	r.renderChildren(node)
	r.write("](")
	r.write(resolveURL(r.base.String(), href))
	r.write(")")
}

func extractText(node *xhtml.Node) string {
	var builder strings.Builder
	var walk func(*xhtml.Node)
	walk = func(current *xhtml.Node) {
		if current.Type == xhtml.TextNode {
			builder.WriteString(current.Data)
			return
		}
		if current.Type == xhtml.ElementNode && strings.EqualFold(current.Data, "br") {
			builder.WriteString("\n")
			return
		}
		for child := current.FirstChild; child != nil; child = child.NextSibling {
			walk(child)
		}
	}
	walk(node)
	return builder.String()
}

func attribute(node *xhtml.Node, key string) string {
	for _, attr := range node.Attr {
		if strings.EqualFold(attr.Key, key) {
			return attr.Val
		}
	}
	return ""
}

func resolveURL(base, ref string) string {
	ref = strings.TrimSpace(ref)
	if ref == "" {
		return ""
	}
	parsed, err := url.Parse(ref)
	if err == nil && parsed.IsAbs() {
		return ref
	}
	baseURL, err := url.Parse(base)
	if err != nil {
		return ref
	}
	resolved, err := baseURL.Parse(ref)
	if err != nil {
		return ref
	}
	return resolved.String()
}

func cleanupMarkdown(markdown string) string {
	markdown = trailingSpacePattern.ReplaceAllString(markdown, "\n")
	return strings.TrimSpace(markdown)
}

func startsWithSpace(s string) bool {
	r, _ := utf8.DecodeRuneInString(s)
	return unicode.IsSpace(r)
}

func endsWithSpace(s string) bool {
	r, _ := utf8.DecodeLastRuneInString(s)
	return unicode.IsSpace(r)
}
