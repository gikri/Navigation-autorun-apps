import 'package:flutter/material.dart';

class BatteryInfo extends StatelessWidget {
  final int batteryLevel;
  final bool isCharging;
  final bool isSmallScreen;

  const BatteryInfo({
    super.key,
    required this.batteryLevel,
    required this.isCharging,
    this.isSmallScreen = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.9),
        borderRadius: BorderRadius.circular(isSmallScreen ? 8 : 12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: isSmallScreen ? 4 : 5,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          // 배터리 아이콘
          Container(
            width: isSmallScreen ? 40 : 50,
            height: isSmallScreen ? 40 : 50,
            decoration: BoxDecoration(
              color: _getBatteryColor().withOpacity(0.1),
              borderRadius: BorderRadius.circular(isSmallScreen ? 6 : 8),
            ),
            child: Icon(
              _getBatteryIcon(),
              color: _getBatteryColor(),
              size: isSmallScreen ? 20 : 24,
            ),
          ),

          SizedBox(width: isSmallScreen ? 8 : 12),

          // 배터리 정보
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  '배터리',
                  style: TextStyle(
                    fontSize: isSmallScreen ? 10 : 12,
                    color: Colors.grey.shade600,
                  ),
                ),
                SizedBox(height: isSmallScreen ? 2 : 4),
                Text(
                  '$batteryLevel%',
                  style: TextStyle(
                    fontSize: isSmallScreen ? 14 : 18,
                    fontWeight: FontWeight.bold,
                    color: _getBatteryColor(),
                  ),
                ),
              ],
            ),
          ),

          SizedBox(width: isSmallScreen ? 4 : 8),

          // 배터리 바
          Container(
            width: isSmallScreen ? 30 : 40,
            height: isSmallScreen ? 4 : 6,
            decoration: BoxDecoration(
              color: Colors.grey.shade200,
              borderRadius: BorderRadius.circular(isSmallScreen ? 2 : 3),
            ),
            child: FractionallySizedBox(
              alignment: Alignment.centerLeft,
              widthFactor: batteryLevel / 100,
              child: Container(
                decoration: BoxDecoration(
                  color: _getBatteryColor(),
                  borderRadius: BorderRadius.circular(isSmallScreen ? 2 : 3),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Color _getBatteryColor() {
    if (isCharging) {
      return Colors.green;
    }
    if (batteryLevel > 50) {
      return Colors.green;
    }
    if (batteryLevel > 20) {
      return Colors.orange;
    }
    return Colors.red;
  }

  IconData _getBatteryIcon() {
    if (isCharging) {
      return Icons.battery_charging_full;
    }
    if (batteryLevel > 80) {
      return Icons.battery_full;
    }
    if (batteryLevel > 50) {
      return Icons.battery_6_bar;
    }
    if (batteryLevel > 20) {
      return Icons.battery_2_bar;
    }
    return Icons.battery_alert;
  }
}
