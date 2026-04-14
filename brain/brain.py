from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from model.client_request import ClientRequest
from prometheus_client import make_asgi_app
from service.llm_service import llm_response

app = FastAPI()
app.mount("/metrics", make_asgi_app(), "prometheus")


@app.get("/health")
def health_check():
    return "Brain is ACTIVE"

@app.post("/tailor")
async def tailor(req: ClientRequest):
    resume_text = req.resume_text
    job_description = req.job_description
    try:
        response = StreamingResponse(llm_response(resume_text, job_description), media_type="text/event-stream")
    except:
        return "Brain DEAD"
    return response