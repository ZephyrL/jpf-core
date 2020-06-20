#!/bin/bash

bin/jpf src/examples/RacerJson.jpf 

echo " -------------- Checking difference with sample output --------------"
diff jsonOutput.json src/resources/Racer.json.checked