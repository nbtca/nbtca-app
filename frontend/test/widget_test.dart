import 'package:flutter_test/flutter_test.dart';
import 'package:nbtca_app/main.dart';

void main() {
  testWidgets('NBTCA App smoke test and main navigation tabs',
      (WidgetTester tester) async {
    await tester.pumpWidget(const NBTCAApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.text('拔电关机'), findsWidgets);
    expect(find.text('维修'), findsOneWidget);
    expect(find.text('工具'), findsOneWidget);
    expect(find.text('个人中心'), findsOneWidget);
  });
}
