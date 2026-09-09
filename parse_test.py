import json

data = """{
  "teacher": {
    "name": "Prof. Test Faculty",
    "id": "t001"
  },
  "batches": [
    {
      "batchId": "b1",
      "year": "2026",
      "semester": "1st Sem",
      "course": {
        "code": "CS-101",
        "name": "Programming Fundamentals"
      },
      "section": "A",
      "location": "Room 101",
      "weeklySchedule": [
        {
          "day": "Mon",
          "time": "9:00 AM - 10:00 AM",
          "location": "Room 101"
        }
      ],
      "students": [
        {
          "id": "b1s1",
          "name": "Sara Gupta",
          "rollNumber": "CS26-001"
        }
      ]
    }
  ]
}"""
try:
    j = json.loads(data)
    print("Valid JSON")
except Exception as e:
    print("Invalid JSON", e)
