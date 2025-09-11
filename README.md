# osrs

Rogue's Den script for the DreamBot client.

## Building the JAR

1. Ensure you have [Maven](https://maven.apache.org/) and Java 8 or newer installed.
2. Fetch the DreamBot API from their Maven repository and build the project:

   ```bash
   mvn package
   ```

   The compiled JAR will be located in `target/roguesden-1.0-SNAPSHOT.jar`.

## Running the Script

1. Copy the built JAR into your DreamBot `Scripts` directory.
2. Launch the DreamBot client and select the script from the script manager.

## Dependencies and Runtime Requirements

- DreamBot API (`org.dreambot:dreambot:3.0.0`, scope `provided`)
- Java 8 or newer
- DreamBot client installation to provide the API at runtime
