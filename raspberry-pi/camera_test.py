import sys
sys.path.append('/usr/lib/python3/dist-packages')
                
import serial
import time
import cv2
import os
import numpy as np
from datetime import datetime
from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
import threading
import subprocess
import speech_recognition as sr
import logging
import tkinter as tk 

# Flask 자체의 통신 로그 전면 차단
log = logging.getLogger('werkzeug')
log.setLevel(logging.ERROR)

app = Flask(__name__)
CORS(app) 

# 아두이노 시리얼 포트 설정
ser = serial.Serial('/dev/serial/by-id/usb-Arduino__www.arduino.cc__0043_55137313931351812240-if00', 9600, timeout=1)
ser.flush()

CAPTURE_DIR = "/home/pi/doorphone"
if not os.path.exists(CAPTURE_DIR):
    os.makedirs(CAPTURE_DIR)

# 🌟 중요: 안드로이드와 아두이노 스레드가 카메라를 절대 동시에 못 건드리게 막는 강력한 단일 자물쇠
camera_lock = threading.Lock()
audio_lock = threading.Lock() 

visit_history_db = []
last_capture_time = 0
record_counter = 1
app_responded_flag = False

current_display_msg = "대기 중...\n방문자 없음"
current_bg_color = "#2C3E50" 
display_label = None 
root = None

os.environ["LIBCAMERA_LOG_LEVELS"] = "*:ERROR"

print("📹 [시스템] 카메라 하드웨어 초기화 및 상시 최적화 개방 중...")
global_cap = cv2.VideoCapture("/dev/video0", cv2.CAP_V4L2)
if global_cap.isOpened():
    global_cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    global_cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
    print("✅ [카메라] 상시 대기 모드 장착 완료. 호환성이 백업되었습니다.")
else:
    print("❌ [카메라] 장치를 열 수 없습니다!")

def 원래볼륨최대화():
    subprocess.run(["amixer", "sset", "Master", "100%"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(["amixer", "sset", "PCM", "100%"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def update_door_display(text, bg_color="#2C3E50"):
    global current_display_msg, current_bg_color
    current_display_msg = text
    current_bg_color = bg_color
    if display_label and root:
        root.after(0, lambda: display_label.config(text=current_display_msg))
        root.after(0, lambda: root.config(bg=current_bg_color))
        root.after(0, lambda: display_label.config(bg=current_bg_color))

def launch_tkinter_display():
    global display_label, root
    root = tk.Tk()
    root.title("스마트 도어폰 디스플레이")
    root.attributes('-fullscreen', True)
    root.config(bg=current_bg_color)
    
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

# 📸 [자물쇠 제어 보강]: 띵동 시 단 한 번의 프레임 누수도 용납하지 않는 촬영 함수
def capture_snapshot(label_text="미지정"):
    global last_capture_time, visit_history_db, record_counter
    filename = None
    
    print(f"📸 [카메라] {label_text} 촬영 격발 엔진 시동!")
    
    # 🌟 안드로이드가 가로채지 못하도록 카메라 문을 꽉 걸어 잠급니다.
    with camera_lock:
        try:
            if global_cap and global_cap.isOpened():
                # 🌟 버퍼 유실 방 가드: 최신 프레임을 가져오기 위해 앞 버퍼를 순식간에 비웁니다.
                for _ in range(5):
                    global_cap.read()
                ret, frame = global_cap.read()
                
                if ret and frame is not None:
                    last_capture_time = time.time()
                    now = datetime.now()
                    timestamp_str = now.strftime("%Y-%m-%d %H:%M:%S")
                    file_timestamp = now.strftime("%Y%m%d_%H%M%S")
                    
                    filename = f"capture_{file_timestamp}.jpg"
                    full_path = f"{CAPTURE_DIR}/{filename}"
                    
                    cv2.imwrite(full_path, frame, [cv2.IMWRITE_JPEG_QUALITY, 50])
                    print(f"💾 [스냅샷 저장 완료]: {filename}") # 이제 무조건 이 로그가 뜹니다!
                    
                    new_record = {
                        "id": record_counter,
                        "timestamp": timestamp_str,
                        "image_name": filename,
                        "label": label_text
                    }
                    visit_history_db.insert(0, new_record)
                    record_counter += 1
                else:
                    print("❌ [카메라] 리눅스 비디오 드라이버 프레임 반환 실패")
            else:
                print("❌ [카메라] 비디오 장치가 유실되었습니다.")
        except Exception as cam_err:
            print(f"❌ 카메라 에러 발생: {cam_err}")
                
    return filename

def play_male_voice(text_to_speak):
    with audio_lock:
        print(f"🔊 [안내 방송]: \"{text_to_speak}\"")
        원래볼륨최대화()
        subprocess.run([
            "espeak", "-vko", 
            f"\"{text_to_speak}\"", 
            "-a", "130", "-p", "30", "-s", "140"   
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def listen_visitor_answer():
    recognizer = sr.Recognizer()
    time.sleep(0.5)
    with sr.Microphone() as source:
        try:
            print("▶️ [마이크] 방문자 답변 대기 시작 (제한시간 10초)...")
            recognizer.adjust_for_ambient_noise(source, duration=0.4)
            audio = recognizer.listen(source, timeout=10, phrase_time_limit=10)
            text = recognizer.recognize_google(audio, language='ko-KR')
            print(f"🗣️ [방문자 실제 대답]: \"{text}\"")
            return text
        except Exception:
            return ""

def arduino_monitor():
    global visit_history_db, app_responded_flag
    print(">>> [스마트 도어폰] 시스템 대기 중... (시리얼 및 AI 자동응답 모드)")
    ser.reset_input_buffer()
    
    while True:
        try:
            if ser.in_waiting > 0:
                line = ser.readline().decode('utf-8', errors='ignore').strip()
                
                if "CAMERA_ON" in line:
                    print("\n🔔 [초인종 격발] 띵동 신호 감지!")
                    app_responded_flag = False
                    update_door_display("-현재상태-\n호출 중... 부모님 확인 대기 중", bg_color="#D35400")
                    
                    capture_snapshot(label_text="초인종 호출")
                    ser.reset_input_buffer()
                    
                    is_responded = False
                    for sec in range(30):
                        time.sleep(1)
                        if app_responded_flag:
                            is_responded = True
                            break
                    
                    if not is_responded:
                        print("🚨 [30초 무응답] AI 자동 대응 시퀀스를 작동합니다.")
                        update_door_display("-현재상태-\nAI 자동 안내 방송 응대 중", bg_color="#2980B9")
                        play_male_voice("누구세요")
                        visitor_text = listen_visitor_answer()
                        time.sleep(0.3)
                        
                        if any(k in visitor_text for k in ["택배", "배달"]):
                            update_door_display("-현재상태-\n자동응답: 문 앞에 두고 가세요", bg_color="#27AE60")
                            play_male_voice("문 앞에 두고 가세요")
                            if visit_history_db: visit_history_db[0]["label"] = "자동응답: 택배/배달 완료"
                        elif any(k in visitor_text for k in ["점검", "관리"]):
                            update_door_display("-현재상태-\n자동응답: 다음에 방문해 주세요", bg_color="#C0392B")
                            play_male_voice("지금은 어려워요 다음에 와주세요")
                            if visit_history_db: visit_history_db[0]["label"] = "자동응답: 점검/관리 거절"
                        else:
                            print("🤫 무응답 침묵 종료.")
                            update_door_display("-현재상태-\n대기 중...\n방문자 없음", bg_color="#2C3E50")
                            if visit_history_db: visit_history_db[0]["label"] = "자동응답: 무응답 종료"
                    
                    print("⏳ [시스템 보호] 중복 신호 차단 루틴 작동...")
                    time.sleep(3.0)
                    update_door_display("-현재상태-\n대기 중...\n방문자 없음", bg_color="#2C3E50")
                    ser.reset_input_buffer()
                    
            time.sleep(0.1)
        except Exception as serial_err:
            time.sleep(1)
            ser.reset_input_buffer()

# ────────────────────────────────────────────────────────
# 🌐 안드로이드 앱 연동 통신 API 구역

@app.route('/display_status', methods=['GET'])
def get_display_status():
    return jsonify({
        "message": current_display_msg.replace("\n", " "),
        "bg_color": current_bg_color
    }), 200

@app.route('/history', methods=['GET'])
def get_history_api():
    return jsonify(visit_history_db), 200

@app.route('/visitor_image', methods=['GET'])
@app.route('/latest_image', methods=['GET'])
def send_latest_image_api():
    global app_responded_flag
    
    # 🌟 안드로이드가 무한 조회 루프로 들어와도, 아두이노가 촬영 중일 때는 락을 걸어 대기 시킵니다!
    with camera_lock:
        try:
            if request.path == '/latest_image':
                app_responded_flag = True
                update_door_display("-현재상태-\n문 열지 마세요!\n보호자 응대 중", bg_color="#8E44AD")
                
                if visit_history_db and visit_history_db[0]["label"] == "초인종 호출":
                    visit_history_db[0]["label"] = "직접 통화"
                
                # 직접 통화 시에도 락이 잡힌 상태이므로 내부 함수를 직접 타지 않고 안전하게 수집
                if global_cap and global_cap.isOpened():
                    for _ in range(3): global_cap.read()
                    ret, frame = global_cap.read()
                    if ret and frame is not None:
                        file_timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                        filename = f"capture_{file_timestamp}.jpg"
                        cv2.imwrite(f"{CAPTURE_DIR}/{filename}", frame, [cv2.IMWRITE_JPEG_QUALITY, 50])
                
            files = [os.path.join(CAPTURE_DIR, f) for f in os.listdir(CAPTURE_DIR) if f.startswith("capture_") and f.endswith(".jpg")]
            if files:
                files.sort(key=os.path.getmtime)
                return send_from_directory(CAPTURE_DIR, os.path.basename(files[-1]))
            return send_from_directory(CAPTURE_DIR, "blank.jpg")
        except Exception as e:
            return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/classify', methods=['POST'])
def receive_keyword():
    global visit_history_db, app_responded_flag
    try:
        params = request.get_json()
        keyword = params.get("type", "미지정")
        app_responded_flag = True
        
        if keyword == "택배":
            update_door_display("-현재상태-\n택배예요, 괜찮아요 :)", bg_color="#27AE60")
        elif keyword == "지인":
            update_door_display("-현재상태-\n지인이 방문했습니다.괜찮아요 :)", bg_color="#2980B9")
        elif keyword == "수상함":
            update_door_display("-현재상태-\n경고: 문 열지 마세요!", bg_color="#C0392B")
        else:
            update_door_display(f"-현재상태-\n보호자 응대 확인: {keyword}", bg_color="#8E44AD")
            
        if visit_history_db:
            visit_history_db[0]["label"] = keyword
        return jsonify({"status": "success"}), 200
    except Exception:
        return jsonify({"status": "error"}), 500

@app.route('/upload_voice', methods=['POST'])
def upload_voice():
    global app_responded_flag
    if 'file' not in request.files: return jsonify({"status": "error"}), 400
    file = request.files['file']
    
    app_responded_flag = True
    update_door_display("-현재상태-\n문 열지 마세요!\n보호자 응대 중", bg_color="#8E44AD")
    save_path = os.path.join(CAPTURE_DIR, "voice_app.wav")
    file.save(save_path)
    
    def play_audio():
        with audio_lock:
            원래볼륨최대화()
            subprocess.run(["cvlc", "--play-and-exit", save_path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    threading.Thread(target=play_audio).start()
    return jsonify({"status": "success"}), 200

@app.route('/listen_voice', methods=['GET'])
def listen_voice():
    record_path = os.path.join(CAPTURE_DIR, "voice_pi.wav")
    with audio_lock:
        subprocess.run(["arecord", "-d", "3", "-f", "cd", record_path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if os.path.exists(record_path):
        return send_from_directory(CAPTURE_DIR, "voice_pi.wav", as_attachment=True)
    return jsonify({"status": "error", "message": "Audio record failed"}), 500

if __name__ == '__main__':
    t_arduino = threading.Thread(target=arduino_monitor)
    t_arduino.daemon = True
    t_arduino.start()
    
    #t_display = threading.Thread(target=launch_tkinter_display)
    #t_display.daemon = True
    #t_display.start()
    
    print("🚀 [교착상태 완벽 해결 마스터 서버] 시스템 가동되었습니다.")
    app.run(host='0.0.0.0', port=5001, threaded=True, debug=False, use_reloader=False)





