## iDetection


iDetection allows you to Monitor your target Network Interface's Incoming Traffic, and possibly detect (and packet capture) any (Distributed) Denial of Service Attacks.


# Setup (Debian)

Step 1, Install Java.
```
apt-get install default-jre -y
```
Step 2, Run iDetection.
```
java -jar iDetection.jar <target_network_interface>
# example: "java -jar iDetection.jar eth0"
```
