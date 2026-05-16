import time
import json
import cgi
from http.server import HTTPServer, BaseHTTPRequestHandler

# Global Mock State
print_state = {
    "state": "Operational",
    "progress": 0.0,
    "start_time": None,
    "duration": 30.0,  # Simulate a 30 second print job
    "fail_at_42": False
}

class MockOctoPrintHandler(BaseHTTPRequestHandler):

    def log_message(self, format, *args):
        # Suppress noisy logging
        pass

    def do_POST(self):
        if self.path == '/api/files/local':
            ctype, pdict = cgi.parse_header(self.headers.get('content-type'))
            fail_header = self.headers.get('X-Simulate-Failure') == 'true'

            if ctype == 'multipart/form-data':
                print_state['state'] = "Printing"
                print_state['progress'] = 0.0
                print_state['start_time'] = time.time()
                print_state['fail_at_42'] = fail_header
                
                self.send_response(201)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({"done": True, "files": {"local": {"name": "mock.gcode"}}}).encode())
                print(f"[MOCK OCTOPRINT] Received GCODE. Virtual Print Started! (Fail Simulation: {fail_header})")
                return
            
        self.send_response(400)
        self.end_headers()

    def do_GET(self):
        if self.path == '/api/job':
            # Calculate progress if printing
            if print_state['state'] == "Printing" and print_state['start_time']:
                elapsed = time.time() - print_state['start_time']
                progress = (elapsed / print_state['duration']) * 100.0
                
                if print_state['fail_at_42'] and progress >= 42.0:
                    print_state['progress'] = 42.0
                    print_state['state'] = "Error: Thermal Runaway Simulation"
                    print("[MOCK OCTOPRINT] ⚠️ Triggering Simulated Hardware Failure at 42%!")
                elif progress >= 100.0:
                    print_state['progress'] = 100.0
                    print_state['state'] = "Completed"
                    print("[MOCK OCTOPRINT] Virtual print completed.")
                else:
                    print_state['progress'] = progress

            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            
            resp = {
                "state": print_state['state'],
                "progress": {
                    "completion": print_state['progress']
                }
            }
            self.wfile.write(json.dumps(resp).encode())
            return
            
        if self.path == '/api/printer':
            # Simulate temperatures
            if print_state['state'] == "Printing" or print_state['state'].startswith("Error"):
                t_actual, t_target = 200.0, 200.0
                b_actual, b_target = 60.0, 60.0
            else:
                t_actual, t_target = 25.0, 0.0
                b_actual, b_target = 25.0, 0.0
                
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            
            resp = {
                "temperature": {
                    "tool0": {"actual": t_actual, "target": t_target},
                    "bed": {"actual": b_actual, "target": b_target}
                }
            }
            self.wfile.write(json.dumps(resp).encode())
            return
            
        self.send_response(404)
        self.end_headers()

if __name__ == '__main__':
    port = 5000
    server = HTTPServer(('localhost', port), MockOctoPrintHandler)
    print(f"[MOCK OCTOPRINT] Starting virtual printer server on http://localhost:{port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    server.server_close()
