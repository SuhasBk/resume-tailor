from pydantic import BaseModel

class ClientRequest(BaseModel):
    resume_text: str
    job_description: str