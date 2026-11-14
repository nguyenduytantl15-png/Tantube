# TanTube

App Android xem YouTube cho dùng riêng (bọc WebView trang m.youtube.com).

## Tính năng
- Chặn quảng cáo: chặn tên miền quảng cáo, xóa dữ liệu quảng cáo trong trang, tự tua/bỏ qua quảng cáo video còn lọt, ẩn banner.
- Phát nền: thoát ra màn hình chính hoặc tắt màn hình, video vẫn phát tiếng.
- Thông báo có nút **Phát/Tạm dừng** và **Đóng**.
- Xem toàn màn hình, tự xoay ngang.
- Mở link YouTube, hoặc **Chia sẻ → TanTube** từ app khác.
- Nút Back: quay lại trang trước; ở trang đầu thì thu nhỏ app (vẫn phát).

## Build APK bằng GitHub
1. Tạo repo mới trên GitHub, tải toàn bộ thư mục này lên (giữ nguyên thư mục `.github`).
2. Vào tab **Actions** → chờ "Build APK" chạy xong (khoảng 3–5 phút).
3. Bấm vào lần chạy → mục **Artifacts** → tải `TanTube-apk` (file zip chứa APK).
4. Giải nén, chép APK vào điện thoại, cài (cho phép "cài từ nguồn không xác định").

App ký bằng khóa cố định `app/tantube.jks`, nên các bản build sau cài đè lên bản cũ được.
Nên để repo ở chế độ **Private**.

## Lưu ý
- Nếu Android tắt app khi chạy nền: Cài đặt → Ứng dụng → TanTube → Pin → **Không hạn chế**.
- Đăng nhập tài khoản Google trong WebView có thể bị Google chặn; app vẫn xem bình thường khi không đăng nhập.
- Khi YouTube đổi giao diện, chặn quảng cáo có thể lọt. Phần cần sửa nằm ở `app/src/main/assets/inject.js`.
