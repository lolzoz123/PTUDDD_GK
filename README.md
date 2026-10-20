# Đồng hồ Widget (Android Studio, Kotlin)

Ứng dụng đồng hồ gồm 5 thành phần:

| # | Tính năng | File chính |
|---|-----------|-----------|
| 1 | Live Wallpaper | `wallpaper/ClockWallpaperService.kt` |
| 2 | Quick Settings Tile | `tile/ClockTiles.kt` |
| 3 | Always-on Display | `ambient/AmbientClockActivity.kt` |
| 4 | Floating Widget | `floating/FloatingClockService.kt` |
| 5 | Adaptive Widget | `widget/ClockWidgetProvider.kt` + `res/layout/widget_*.xml` |

## Chạy thử
1. Android Studio: **File > Open** > chọn thư mục `ClockWidgetApp`, đợi Gradle sync.
   (Nếu Studio gợi ý nâng cấp AGP/Gradle, cứ chấp nhận.)
2. Chạy trên máy thật hoặc emulator API 26+ (khuyến nghị API 33+ để thử đủ tính năng).
3. Trong app, mở từng thẻ 1 → 5 để bật tính năng tương ứng.

## Lưu ý
- Widget nổi cần quyền *Hiển thị trên các ứng dụng khác*; Android 13+ cần thêm quyền thông báo.
- Android 14+: Foreground service dùng loại `specialUse`. Khi đưa lên Google Play phải khai báo lý do ở Play Console.
- AOD hệ thống của hãng (Samsung, Pixel...) không cho app bên thứ ba chèn nội dung. Dự án dùng
  `AmbientClockActivity` (hiện trên màn hình khóa) và `widgetCategory="home_screen|keyguard"`
  để widget hiện được trên màn hình khóa ở thiết bị hỗ trợ (Pixel Tablet, Android 16 QPR1+ tuỳ nhà sản xuất).
- Widget làm mới pin/báo thức mỗi ~15 phút; giờ và ngày do `TextClock` tự cập nhật.
