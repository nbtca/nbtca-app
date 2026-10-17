/// NBTCA 服务与工单时间统一按北京时间（UTC+8）展示，
/// 避免设备时区为 UTC 时出现 8 小时偏差。
const Duration chinaOffset = Duration(hours: 8);

DateTime toChinaTime(DateTime value) => value.toUtc().add(chinaOffset);

DateTime chinaTimeFromEpoch(int seconds) =>
    DateTime.fromMillisecondsSinceEpoch(seconds * 1000, isUtc: true)
        .add(chinaOffset);

String formatChinaDateTime(DateTime value) {
  final t = toChinaTime(value);
  return '${t.month}月${t.day}日 ${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
}
