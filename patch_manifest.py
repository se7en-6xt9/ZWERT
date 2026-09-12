import re

with open("app/src/main/AndroidManifest.xml", "r") as f:
    content = f.read()

content = content.replace(
    'android:exported="true"',
    'android:exported="true"\n            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize"'
)

with open("app/src/main/AndroidManifest.xml", "w") as f:
    f.write(content)
