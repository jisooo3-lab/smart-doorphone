import tkinter as tk
import requests
import threading
import time

# 🌟 [필수 수정]: 위 1단계에서 확인한 '기존 메인 라즈베리파이'의 IP 주소로 고쳐주세요!
MAIN_PI_IP = "172.20.10.9"  
MAIN_PI_URL = f"http://{MAIN_PI_IP}:5000/display_status"

# GUI 내부 변수 초기화
current_display_msg = "대기 중...\n방문자 없음"
current_bg_color = "#2C3E50"
display_label = None
root = None

# 📡 메인 파이로부터 1초 주기로 상태를 받아오는 백그라운드 스레드 루프
def fetch_status_loop():
    global current_display_msg, current_bg_color
    print(f"📡 [원격 디스플레이] 메인 파이({MAIN_PI_IP}) 서버 동기화 루프 가동...")
    
    while True:
        try:
            # 메인 파이 Flask 웹 서버에 상태 조회 요청
            response = requests.get(MAIN_PI_URL, timeout=2)
            
            if response.status_code == 200:
                data = response.json()
                msg = data.get("message", "대기 중...\n방문자 없음")
                bg_color = data.get("bg_color", "#2C3E50")
                
                # 가독성을 위해 한 줄 공백 텍스트 형식을 실제 줄바꿈(\n)으로 역변환
                if "-현재상태-" in msg:
                    msg = msg.replace("-현재상태- ", "-현재상태-\n")
                
                current_display_msg = msg
                current_bg_color = bg_color
                
                # tkinter UI 갱신 (after 비동기 예약으로 안전성 확보)
                if display_label and root:
                    root.after(0, lambda: display_label.config(text=current_display_msg))
                    root.after(0, lambda: root.config(bg=current_bg_color))
                    root.after(0, lambda: display_label.config(bg=current_bg_color))
            else:
                print(f"⚠️ 서버 응답 코드 에러: {response.status_code}")
                
        except Exception as e:
            # 메인 서버가 꺼져있거나 와이파이가 끊겼을 때 화면 처리
            current_display_msg = "네트워크 연결 대기 중...\n메인 서버를 확인하세요"
            current_bg_color = "#7F8C8D"
            if display_label and root:
                root.after(0, lambda: display_label.config(text=current_display_msg))
                root.after(0, lambda: root.config(bg=current_bg_color))
                root.after(0, lambda: display_label.config(bg=current_bg_color))
        
        time.sleep(1) # 1초 주기로 무한 반복

# 🖥️ 지수님의 검증된 오리지널 순정 전체화면 GUI 윈도우 생성 함수
def launch_display():
    global display_label, root
    root = tk.Tk()
    root.title("현관 전광판 원격 클라이언트 스크린")
    
    root.attributes('-fullscreen', True)
    root.config(bg=current_bg_color)
    
    # ESC 누르면 화면이 꺼지도록 키바인딩 가드 처리
    root.bind('<Escape>', lambda event: root.destroy())
    root.focus_force()
    
    display_label = tk.Label(
        root, 
        text=current_display_msg, 
        font=("Helvetica", 45, "bold"), 
        fg="white", 
        bg=current_bg_color,
        justify="center"
    )
    display_label.pack(expand=True, fill="both")
    root.mainloop()

if __name__ == '__main__':
    # 메인 루프를 독점하지 않기 위해 통신 스레드 분리
    t_sync = threading.Thread(target=fetch_status_loop)
    t_sync.daemon = True
    t_sync.start()
    
    # 메인 스레드에서는 전체화면 GUI만 런칭
    launch_display()
