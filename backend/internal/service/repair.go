package service

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
)

// RepairService provides AI-assisted pre-diagnosis. When Dify is configured it
// calls the blocking workflow API; otherwise (or on any upstream failure) it
// falls back to the local keyword rules that previously lived in the mock BFF.
type RepairService struct {
	cfg    *config.Config
	client *http.Client
}

func NewRepairService(cfg *config.Config, client *http.Client) *RepairService {
	return &RepairService{cfg: cfg, client: newHTTPClient(cfg, client)}
}

func (s *RepairService) Diagnose(ctx context.Context, req model.DiagnosisRequest) model.DiagnosisResponse {
	fallback := heuristicDiagnosis(req)
	if s.cfg == nil || s.cfg.DifyAPIEndpoint == "" || s.cfg.DifyAPIKey == "" {
		return fallback
	}
	result, ok := s.diagnoseWithDify(ctx, req, fallback)
	if !ok {
		return fallback
	}
	return result
}

func (s *RepairService) diagnoseWithDify(ctx context.Context, req model.DiagnosisRequest, fallback model.DiagnosisResponse) (model.DiagnosisResponse, bool) {
	payload, err := json.Marshal(map[string]interface{}{
		"inputs": map[string]interface{}{
			"device_type": req.DeviceType,
			"description": req.Description,
		},
		"response_mode": "blocking",
		"user":          "nbtca-app",
	})
	if err != nil {
		return fallback, false
	}
	body, err := doRequest(ctx, s.client, http.MethodPost, s.cfg.DifyAPIEndpoint+"/workflows/run", payload, map[string]string{
		"Authorization": "Bearer " + s.cfg.DifyAPIKey,
		"Content-Type":  "application/json",
	})
	if err != nil {
		return fallback, false
	}

	var response struct {
		Data struct {
			Status  string                 `json:"status"`
			Outputs map[string]interface{} `json:"outputs"`
			Error   interface{}            `json:"error"`
		} `json:"data"`
	}
	if err := json.Unmarshal(body, &response); err != nil {
		return fallback, false
	}
	if response.Data.Error != nil && fmt.Sprint(response.Data.Error) != "<nil>" {
		return fallback, false
	}
	if response.Data.Status != "" && response.Data.Status != "succeeded" && response.Data.Status != "success" {
		return fallback, false
	}
	outputs := response.Data.Outputs
	if nested, ok := outputs["result"].(map[string]interface{}); ok {
		outputs = nested
	}
	if len(outputs) == 0 {
		return fallback, false
	}

	result := fallback
	mapped := false
	if value, ok := firstOutput(outputs, "possible_causes", "possibleCauses"); ok {
		if list := toStringSlice(value); len(list) > 0 {
			result.PossibleCauses = list
			mapped = true
		}
	}
	if value, ok := firstOutput(outputs, "self_help_steps", "selfHelpSteps"); ok {
		if list := toStringSlice(value); len(list) > 0 {
			result.SelfHelpSteps = list
			mapped = true
		}
	}
	if value, ok := firstOutput(outputs, "recommended_tools", "recommendedTools"); ok {
		if list := toStringSlice(value); len(list) > 0 {
			result.RecommendedTools = list
			mapped = true
		}
	}
	if value, ok := firstOutput(outputs, "suggest_repair", "suggestRepair"); ok {
		result.SuggestRepair = toBool(value)
		mapped = true
	}
	if value, ok := firstOutput(outputs, "summary"); ok {
		if text := toString(value); text != "" {
			result.Summary = text
			mapped = true
		}
	}
	if !mapped {
		return fallback, false
	}
	result.Source = "dify"
	return result, true
}

func firstOutput(outputs map[string]interface{}, keys ...string) (interface{}, bool) {
	for _, key := range keys {
		if value, ok := outputs[key]; ok && value != nil {
			return value, true
		}
	}
	return nil, false
}

func toStringSlice(value interface{}) []string {
	switch typed := value.(type) {
	case []string:
		return cleanNonEmpty(typed)
	case []interface{}:
		out := make([]string, 0, len(typed))
		for _, item := range typed {
			if text := toString(item); text != "" {
				out = append(out, text)
			}
		}
		return out
	case string:
		raw := strings.TrimSpace(typed)
		if raw == "" {
			return nil
		}
		var decoded []string
		if json.Unmarshal([]byte(raw), &decoded) == nil {
			return cleanNonEmpty(decoded)
		}
		parts := strings.FieldsFunc(raw, func(r rune) bool {
			return r == '\n' || r == ';' || r == '；'
		})
		return cleanNonEmpty(parts)
	default:
		if text := toString(value); text != "" {
			return []string{text}
		}
		return nil
	}
}

func toString(value interface{}) string {
	switch typed := value.(type) {
	case nil:
		return ""
	case string:
		return strings.TrimSpace(typed)
	case json.Number:
		return typed.String()
	case float64:
		if typed == float64(int64(typed)) {
			return fmt.Sprintf("%d", int64(typed))
		}
		return fmt.Sprintf("%v", typed)
	default:
		return strings.TrimSpace(fmt.Sprint(typed))
	}
}

func toBool(value interface{}) bool {
	switch typed := value.(type) {
	case bool:
		return typed
	case string:
		switch strings.ToLower(strings.TrimSpace(typed)) {
		case "true", "1", "yes", "是":
			return true
		}
	case float64:
		return typed != 0
	case json.Number:
		return typed.String() != "0"
	}
	return false
}

func cleanNonEmpty(values []string) []string {
	out := make([]string, 0, len(values))
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value != "" {
			out = append(out, value)
		}
	}
	return out
}

func heuristicDiagnosis(req model.DiagnosisRequest) model.DiagnosisResponse {
	text := strings.ToLower(req.Description + " " + req.DeviceType)
	resp := model.DiagnosisResponse{
		DeviceType: req.DeviceType,
		Source:     "heuristic",
	}

	if strings.Contains(text, "蓝屏") || strings.Contains(text, "dump") {
		resp.PossibleCauses = []string{
			"系统第三方驱动冲突（常见为核显/显卡驱动、杀毒软件驱动）",
			"内存接触不良或 XMP 超频不稳定",
			"固态硬盘损坏、坏道或过热掉盘",
		}
		resp.SelfHelpSteps = []string{
			"开机按 F8 / Shift 重启进入安全模式，排查最近更新的驱动或软件",
			"利用 Win+X 打开终端，运行 sfc /scannow 与 dism 修复系统文件",
			"使用 BluescreenView 或 WinDbg 查看 C:\\Windows\\Minidump 分析报错模块",
		}
		resp.RecommendedTools = []string{"Dism++", "MemTest64", "CrystalDiskInfo"}
		resp.SuggestRepair = true
		resp.Summary = "检测到蓝屏故障，建议保存 Minidump 并在本周六携带笔记本到活动室，技术员可协助提取转储日志并重装干净系统。"
		return resp
	}

	if strings.Contains(text, "进水") || strings.Contains(text, "倒水") {
		resp.PossibleCauses = []string{
			"主板供电短路损坏",
			"键盘内部导电薄膜腐蚀",
			"电池短路甚至鼓包危险",
		}
		resp.SelfHelpSteps = []string{
			"【紧急】切勿尝试开机！切勿插充电器！",
			"立即拔掉所有外接电源与外设，若为可拆卸电池请立即断电",
			"倒扣笔记本并放置在通风处，尽快送往维修点进行主板超声波洗板和烘干",
		}
		resp.RecommendedTools = []string{"无水乙醇洗板水", "防静电手套", "螺丝刀套装"}
		resp.SuggestRepair = true
		resp.Summary = "设备进水风险极高，请立刻完全断电勿二次通电，建议立即送修让技术员拆机拔电池断电清洗！"
		return resp
	}

	if strings.Contains(text, "风扇") || strings.Contains(text, "发热") || strings.Contains(text, "卡顿") {
		resp.PossibleCauses = []string{
			"出风口硅脂干涸，散热鳍片被灰尘柳絮堵死",
			"后台常驻软件占用高 CPU",
			"散热策略或电源选项被锁在省电模式",
		}
		resp.SelfHelpSteps = []string{
			"任务管理器查看 CPU 和 GPU 占用前三的进程并排查异常进程",
			"查看温度监控（使用 AIDA64 或 HWMonitor），若待机超 70℃ 需清灰换硅脂",
		}
		resp.RecommendedTools = []string{"信越 7921 / 霍尼韦尔相变片", "压缩空气罐", "AIDA64"}
		resp.SuggestRepair = true
		resp.Summary = "机器可能积灰或硅脂老化，建议参加协会本周六义务维修活动，可免费获得清灰除尘和硅脂重涂服务。"
		return resp
	}

	resp.PossibleCauses = []string{
		"常规硬件接触松动或静电堆积",
		"操作系统引导文件异常或系统组件损坏",
	}
	resp.SelfHelpSteps = []string{
		"释放静电：长按电源键 30 秒进行静电释放后重试",
		"使用 PE 启动盘进入临时系统排查硬件识别情况",
	}
	resp.RecommendedTools = []string{"WePE 启动盘", "图吧工具箱"}
	resp.SuggestRepair = true
	resp.Summary = "故障原因较复杂，建议直接提交工单，由 NBTCA 技术骨干一对一在线诊断或线下当面检测。"
	return resp
}
