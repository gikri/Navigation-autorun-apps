import 'package:flutter/material.dart';

class ControlButtons extends StatelessWidget {
  final VoidCallback onActivateService;
  final VoidCallback onDeactivateService;
  final bool hasTargetApp;
  final bool isServiceActive;
  final bool isSmallScreen;

  const ControlButtons({
    super.key,
    required this.onActivateService,
    required this.onDeactivateService,
    required this.hasTargetApp,
    required this.isServiceActive,
    required this.isSmallScreen,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        // 앱 활성화 버튼
        Expanded(
          child: ElevatedButton.icon(
            onPressed:
                hasTargetApp && !isServiceActive ? onActivateService : null,
            icon: Icon(
              Icons.play_arrow,
              size: isSmallScreen ? 18 : 24,
              color: Colors.white,
            ),
            label: Text(
              '앱 활성화',
              style: TextStyle(fontSize: isSmallScreen ? 12 : 14),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor:
                  hasTargetApp && !isServiceActive ? Colors.green : Colors.grey,
              foregroundColor: Colors.white,
              padding: EdgeInsets.symmetric(vertical: isSmallScreen ? 12 : 16),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(isSmallScreen ? 8 : 12),
              ),
            ),
          ),
        ),

        SizedBox(width: isSmallScreen ? 8 : 12),

        // 앱 비활성화 버튼
        Expanded(
          child: ElevatedButton.icon(
            onPressed:
                hasTargetApp && isServiceActive ? onDeactivateService : null,
            icon: Icon(
              Icons.pause,
              size: isSmallScreen ? 18 : 24,
              color: Colors.white,
            ),
            label: Text(
              '앱 비활성화',
              style: TextStyle(fontSize: isSmallScreen ? 12 : 14),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor:
                  hasTargetApp && isServiceActive ? Colors.red : Colors.grey,
              foregroundColor: Colors.white,
              padding: EdgeInsets.symmetric(vertical: isSmallScreen ? 12 : 16),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(isSmallScreen ? 8 : 12),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
