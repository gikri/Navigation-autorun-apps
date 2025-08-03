import 'package:flutter/material.dart';

class TopStatusBox extends StatelessWidget {
  final bool isCharging;
  final bool isConnected;
  final bool isAppRunning;
  final String? targetApp;
  final bool isSmallScreen;

  const TopStatusBox({
    super.key,
    required this.isCharging,
    required this.isConnected,
    required this.isAppRunning,
    this.targetApp,
    this.isSmallScreen = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: EdgeInsets.all(isSmallScreen ? 8.0 : 16.0),
      padding: EdgeInsets.symmetric(
        horizontal: isSmallScreen ? 12.0 : 16.0,
        vertical: isSmallScreen ? 8.0 : 12.0,
      ),
      decoration: BoxDecoration(
        color: _getBackgroundColor(),
        borderRadius: BorderRadius.circular(isSmallScreen ? 8.0 : 12.0),
        border: Border.all(color: _getBorderColor(), width: 2.0),
        boxShadow: [
          BoxShadow(
            color: _getBackgroundColor().withOpacity(0.3),
            blurRadius: 8.0,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          // 상태 아이콘
          Container(
            padding: EdgeInsets.all(isSmallScreen ? 6.0 : 8.0),
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.9),
              shape: BoxShape.circle,
            ),
            child: Icon(
              _getStatusIcon(),
              color: _getIconColor(),
              size: isSmallScreen ? 20.0 : 24.0,
            ),
          ),

          SizedBox(width: isSmallScreen ? 8.0 : 12.0),

          // 상태 텍스트
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  _getMainStatusText(),
                  style: TextStyle(
                    fontSize: isSmallScreen ? 14.0 : 16.0,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                SizedBox(height: isSmallScreen ? 2.0 : 4.0),
                Text(
                  _getSubStatusText(),
                  style: TextStyle(
                    fontSize: isSmallScreen ? 11.0 : 12.0,
                    color: Colors.white.withOpacity(0.9),
                  ),
                ),
              ],
            ),
          ),

          // 상태 표시 점
          Container(
            width: isSmallScreen ? 8.0 : 10.0,
            height: isSmallScreen ? 8.0 : 10.0,
            decoration: BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
            ),
          ),
        ],
      ),
    );
  }

  Color _getBackgroundColor() {
    if (targetApp == null) {
      return Colors.grey.shade600;
    }
    if (isAppRunning) {
      return Colors.green.shade600;
    }
    return Colors.grey.shade500; // 비활성화 상태도 회색 계열 사용
  }

  Color _getBorderColor() {
    if (targetApp == null) {
      return Colors.grey.shade400;
    }
    if (isAppRunning) {
      return Colors.green.shade400;
    }
    return Colors.grey.shade300; // 비활성화 상태 경계선
  }

  IconData _getStatusIcon() {
    if (targetApp == null) {
      return Icons.apps;
    }
    if (isAppRunning) {
      return Icons.play_circle_filled;
    }
    return Icons.stop_circle; // 비활성화 상태 아이콘
  }

  Color _getIconColor() {
    if (targetApp == null) {
      return Colors.grey.shade600;
    }
    if (isAppRunning) {
      return Colors.green.shade600;
    }
    return Colors.grey.shade600; // 비활성화 상태 아이콘 색상
  }

  String _getMainStatusText() {
    if (targetApp == null) {
      return '앱 미선택';
    }
    if (isAppRunning) {
      return '앱 실행 중';
    }
    return '앱 비활성화'; // 기본값을 비활성화로 변경
  }

  String _getSubStatusText() {
    if (targetApp == null) {
      return '네비게이션 앱을 선택해주세요';
    }

    if (isAppRunning) {
      return '${_getAppName(targetApp!)}이 실행 중입니다';
    }
    return '${_getAppName(targetApp!)}이 비활성화 상태입니다'; // 비활성화 상태 설명
  }

  String _getAppName(String packageName) {
    switch (packageName) {
      case 'com.skt.tmap.ku':
        return 'T맵';
      case 'com.kakao.navi':
        return '카카오네비';
      case 'net.daum.android.map':
        return '카카오맵';
      case 'com.nhn.android.nmap':
        return '네이버맵';
      default:
        return '네비게이션 앱';
    }
  }
}
