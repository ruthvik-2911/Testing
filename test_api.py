import requests
import json

API_URL = "http://15.206.186.192:3000/v1"
EMAIL = "admin@keliri.com"
PASSWORD = "Password@123"

def test_mobilize():
    resp = requests.post(f"{API_URL}/user/login?authType=EMAIL&userType=BACKOFFICE", 
                         json={"emailAddress": EMAIL, "password": PASSWORD})
    raw_data = resp.json()
    print("Raw Response Keys:", raw_data.keys())
    content = raw_data.get("data")
    print("Content Type:", type(content))
    if isinstance(content, str):
        print("Content is string! Parsing...")
        parsed = json.loads(content)
        print("Parsed Keys:", parsed.keys())
        token = parsed.get("token")
        print("Token found:", token is not None)
    else:
        print("Content is already dict")

if __name__ == "__main__":
    test_mobilize()
