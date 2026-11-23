import os
import sys
import subprocess
import webbrowser

def launch_ultra_studio():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    html_path = os.path.join(base_dir, "stickman_2d_studio.html")
    if not os.path.exists(html_path):
        html_path = "F:/2D/stickman_2d_studio.html"
    file_url = "file:///" + html_path.replace("\\", "/")

    # محاولة فتح التطبيق كنافذة تطبيق سطح مكتب مستقلة عبر Chrome أو Edge (App Mode)
    browser_candidates = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    ]
    for exe in browser_candidates:
        if os.path.exists(exe):
            try:
                subprocess.Popen([exe, f"--app={file_url}", "--start-maximized"])
                print("SUCCESS: Launched Stickman 2D Studio ULTRA Desktop Window!")
                return
            except Exception:
                pass

    webbrowser.open(file_url)
    print("SUCCESS: Opened Stickman 2D Studio ULTRA in browser!")

if __name__ == "__main__":
    launch_ultra_studio()
