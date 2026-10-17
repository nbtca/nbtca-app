import 'package:flutter/material.dart';
import '../../app/theme/app_colors.dart';

class StatusBadge extends StatelessWidget {
  final String status;
  final String label;

  const StatusBadge({
    super.key,
    required this.status,
    required this.label,
  });

  Color _getColor() {
    switch (status) {
      case 'open':
      case 'pending':
        return AppColors.statusPending;
      case 'accepted':
      case 'repairing':
        return AppColors.statusAccepted;
      case 'committed':
        return AppColors.statusRepairing;
      case 'closed':
      case 'completed':
        return AppColors.statusCompleted;
      case 'cancelled':
      case 'canceled':
        return AppColors.statusCancelled;
      default:
        return AppColors.primary;
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = _getColor();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.3), width: 0.8),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
