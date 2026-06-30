from fastapi import FastAPI, HTTPException
import requests
from bs4 import BeautifulSoup

app = FastAPI()

@app.get("/")
def home():
    return {"message": "Website Metadata API is running"}

@app.get("/metadata")
def get_metadata(url: str):
    try:
        response = requests.get(url, timeout=5, headers={"User-Agent": "Mozilla/5.0"})
        response.raise_for_status()
    except requests.RequestException as e:
        raise HTTPException(status_code=400, detail=f"Could not fetch URL: {e}")

    soup = BeautifulSoup(response.text, "html.parser")

    def get_meta(name):
        tag = soup.find("meta", attrs={"name": name}) or soup.find("meta", attrs={"property": name})
        return tag["content"] if tag and tag.has_attr("content") else None

    title = soup.title.string.strip() if soup.title else None

    return {
        "url": url,
        "title": title,
        "description": get_meta("description") or get_meta("og:description"),
        "og_title": get_meta("og:title"),
        "og_image": get_meta("og:image"),
        "og_site_name": get_meta("og:site_name"),
    }