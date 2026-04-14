import os
import time
from dotenv import load_dotenv
from groq import Groq
from prometheus_client import Counter, Histogram

load_dotenv("api.env")
load_dotenv("prompt.env")
client = Groq()
success_counter = Counter("brain_success", "All successful brain requests counter")
failure_counter = Counter("brain_failure", "All failed brain requests counter")

LATENCY = Histogram("brain_llm_inference_seconds", "Time spent in LLM loop")
USER_PROMPT = """RESUME CONTENT: {resume_text}. JOB_DESCRIPTION: {job_description}.
In terms of percentage, how close does this resume match with the job description?
Your output should be in percentage only!"""

async def llm_response(resume_text, job_description):
    start_time = time.time()
    try:
        completion = client.chat.completions.create(
            model=os.getenv("BRAIN_LLM"),
            messages=[
            {
                "role": "system",
                "content": os.getenv("BRAIN_SYSTEM_PROMPT")
            },
            {
                "role": "user",
                "content": USER_PROMPT.format_map({
                    "resume_text": resume_text,
                    "job_description": job_description
                })
            }
            ],
            temperature=0.2,
            max_completion_tokens=4096,
            top_p=0.95,
            reasoning_effort="default",
            reasoning_format="hidden",
            stream=True,
            stop=None,
        )

        for chunk in completion:
            yield chunk.choices[0].delta.content or ""
        success_counter.inc()
        LATENCY.observe(time.time() - start_time)
    except Exception:
        failure_counter.inc()