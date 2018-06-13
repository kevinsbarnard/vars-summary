# vars-summary
Summarizes number of images and quality images of species specified by genus from the VARS database.

## Usage
To run vars-summary from the command line, run the following from the project directory:

```bash
cd build/libs
java -jar vars-summary-1.0-all.jar path/to/concepts.txt
```

### Options
To specify the output file, use the option `-o`, followed by the path to the output file. Example:

```bash
java -jar vars-summary-1.0-all.jar -o /usr/home/results.csv
```

To use the MBARI kb server to fetch a list of all genera, use the option `-kb`. Example:

```bash
java -jar vars-summary-1.0-all.jar -o /usr/home/all_genera.csv
```

### Genus list file
A proper concept list is simply a `.txt` file with one genus per line. Example:

```
Apolemia
Gonatus
Bathochordaeus
Solmissus
Nanomia
Aegina
```

## Build
To build the vars-summary, you must have the latest version of [Gradle](https://gradle.org/). Run the following from the project directory:

```bash
gradle shadowJar
```

The executable jar file will be located at build/libs/vars-summary-1.0-all.jar
