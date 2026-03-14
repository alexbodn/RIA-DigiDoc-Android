#!/bin/bash
kotlinc NfcSmartCardReader.kt -cp ../classes.jar -include-runtime -d NfcSmartCardReader.jar
unzip NfcSmartCardReader.jar
