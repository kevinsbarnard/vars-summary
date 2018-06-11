# vars-summary
Summarizes number of images and quality images of species specified by genus from the VARS database.

# Usage
To run vars-summary from the command line, run the following from the project directory:
```bash
cd build/libs
java -jar vars-summary-1.0-all.jar path/to/concepts.txt
```

# Build
To build the vars-summary, you must have the latest version of [Gradle](https://gradle.org/). Run the following from the project directory:
```bash
gradle shadowJar
```
The executable jar file will be located at build/libs/vars-summary-1.0-all.jar
