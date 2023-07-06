/*
Copyright (c) 2023 Paul Uszak.  Email: paul.uszak (at) gmail.com

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

// spell-checker: disable 

package com.reallyreallyrandom.ent3000;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.random.RandomGenerator;

import org.json.simple.JSONObject;

import com.reallyreallyrandom.ent3000.thetests.Chi;
import com.reallyreallyrandom.ent3000.thetests.Compression;
import com.reallyreallyrandom.ent3000.thetests.Entropy;
import com.reallyreallyrandom.ent3000.thetests.ITestish;
import com.reallyreallyrandom.ent3000.thetests.Mean;
import com.reallyreallyrandom.ent3000.thetests.Pi;
import com.reallyreallyrandom.ent3000.thetests.Sanity;
import com.reallyreallyrandom.ent3000.thetests.UnCorrelation;

public class Suite {

    final static double ALPHA = 0.05;
    final static double SANITY_ALPHA = 0.001;
    final static int[] ACCEPTABLE_SAMPLE_SIZES = { 25_000, 50_000, 75_000, 100_000, 150_000, 200_000, 300_000, 400_000,
            500_000, 600_000,
            700_000, 800_000, 900_000, 1_000_000 };
    private static boolean isJsonOutputEnabled = false;

    // TODO Use library like https://picocli.info/ for arguments handling.
    public static void main(String[] args) {
        System.out.println("\nent3000 starting...");
        System.out.println("--help option to display this help.");
        String inFile = null;
        String outFile = null;

        try {
            if (args.length > 0) {
                inFile = args[0];
                if (args[0].equals("--help")) {
                    CommonStuff cs = new CommonStuff();
                    String help = cs.readFromJARFile("help.txt");
                    System.out.println(help);
                    System.exit(0);
                }
                if (args[0].equals("-j")) {
                    isJsonOutputEnabled = true;
                    System.out.println("JSON output enabled.");
                    if (args.length > 1)
                    {
                        inFile = args[1];
                        if (args.length < 3)
                        {
                            throw new Exception("No output file provided.");
                        }
                        else
                        {
                            outFile = args[2];
                        }
                    }
                    else
                    {
                        inFile = "internal_CSPRNG";
                        outFile = "internal_CSPRNG_results.json";
                    }
                    System.out.println("JSON output path: " + outFile);
                }
            } else {
                inFile = "internal_CSPRNG";
            }
            Suite ent = new Suite();
            ent.runTests(inFile, outFile);
        } catch (Exception e) {
            System.err.println("Problem testing file: " + inFile);
            System.err.println(e.getMessage());
            System.err.println("Exit.");
        }
    }

    // TODO Is (pValue == -2) to be dealt with here too?
    public void prettyPrintResult(String testName, double pValue, String testComment) {
        if (pValue == -1) {
            System.out.printf("%-15s  %s  %n", testName + ",", "OoC,          FAIL.");
        } else {
            String pOutput = "p = " + String.format("%.3f", pValue) + ",  ";
            System.out.printf("%-15s  %s  %s  %n", testName + ",", pOutput, testComment);
        }
    }

    // TODO Explain the justification for these, i.e. the 25_000 min. and the
    // 1_000_000 max.
    public byte[] truncate(byte[] array) {
        if (array.length < ACCEPTABLE_SAMPLE_SIZES[0]) {
            System.err.println("Sample file is too small.");
            System.err.println("Exit.");
            System.exit(-1);
        }

        byte[] truncated = null;
        for (int i = ACCEPTABLE_SAMPLE_SIZES.length - 1; i >= 0; i--) {
            int target = ACCEPTABLE_SAMPLE_SIZES[i];

            if (array.length >= target) {
                truncated = new byte[target];
                System.arraycopy(array, 0, truncated, 0, target);
                break;
            }
        }
        return truncated;
    }

    public void runTests(String inputFilename, String outputFilename) throws IOException, NoSuchAlgorithmException {
        // TODO Break this out into a method?
        byte[] samples;
        HashMap<String, Object> runResult = new HashMap<String, Object>();
        ArrayList<Object> testResults = new ArrayList<Object>();
        if (inputFilename == "internal_CSPRNG") {
            RandomGenerator rng = SecureRandom.getInstance("NativePRNG");
            int noFakeSamples = ACCEPTABLE_SAMPLE_SIZES[rng.nextInt(ACCEPTABLE_SAMPLE_SIZES.length)];
            samples = new byte[noFakeSamples];
            rng.nextBytes(samples);
            System.out.println("Testing internal native CSPRNG.");
            System.out.println("Testing " + samples.length + " bytes.");
        } else {
            Path inputFilePath = Paths.get(inputFilename);
            samples = Files.readAllBytes(inputFilePath);
            samples = truncate(samples);
            System.out.println("Testing first " + samples.length + " bytes.");
            runResult.put("Filename", inputFilePath.getFileName().toString());
        }

        ITestish test;
        double pValue;
        String testComment = null;
        runResult.put("BytesTested", samples.length);
        test = new Sanity();
        pValue = test.getPValue(samples);
        if (pValue > SANITY_ALPHA) {
            if (isJsonOutputEnabled) {
                runResult.put("Sane", true);
            }
            System.out.println("Sane sample file. Good.");
            System.out.println("------------------------------------");
        } else {
            if (isJsonOutputEnabled) {
                runResult.put("Sane", false);
                JSONObject runResultJsonObject = new JSONObject(runResult);
                FileWriter file = new FileWriter(outputFilename);
                file.write(runResultJsonObject.toJSONString());
                file.close();
            }
            System.out.println("Insane sample file. Bad.");
            System.out.println("Exit.");
            System.exit(-1);
        }

        HashMap<String, Object> entropyTestResult = new HashMap<String, Object>();
        Boolean isTestPass = false;
        test = new Entropy();
        pValue = test.getPValue(samples);
        if (pValue > ALPHA) {
            testComment = "PASS.";
            isTestPass = true;
        } else {
            testComment = "FAIL.";
            isTestPass = false;
        }
        if (isJsonOutputEnabled) {
            entropyTestResult.put("Name", "Entropy");
            entropyTestResult.put("PValue", pValue);
            entropyTestResult.put("Pass", isTestPass);
            testResults.add(entropyTestResult);
        }
        prettyPrintResult("Entropy", pValue, testComment);
        
        HashMap<String, Object> compressionTestResult = new HashMap<String, Object>();
        test = new Compression();
        pValue = test.getPValue(samples);
        if (pValue > ALPHA) {
            testComment = "PASS.";
            isTestPass = true;
        } else {
            testComment = "FAIL.";
            isTestPass = false;
        }
        if (isJsonOutputEnabled) {
            compressionTestResult.put("Name", "Compression");
            compressionTestResult.put("PValue", pValue);
            compressionTestResult.put("Pass", isTestPass);
            testResults.add(compressionTestResult);
        }
        prettyPrintResult("Compression", pValue, testComment);

        HashMap<String, Object> chiTestResult = new HashMap<String, Object>();
        test = new Chi();
        pValue = test.getPValue(samples);
        if (pValue > ALPHA) {
            testComment = "PASS.";
            isTestPass = true;
        } else {
            testComment = "FAIL.";
            isTestPass = false;
        }
        if (isJsonOutputEnabled) {
            chiTestResult.put("Name", "Chi");
            chiTestResult.put("PValue", pValue);
            chiTestResult.put("Pass", isTestPass);
            testResults.add(chiTestResult);
        }
        prettyPrintResult("Chi", pValue, testComment);
        
        HashMap<String, Object> meanTestResult = new HashMap<String, Object>();
        test = new Mean();
        pValue = test.getPValue(samples);
        if (pValue > ALPHA) {
            testComment = "PASS.";
            isTestPass = true;
        } else {
            testComment = "FAIL.";
            isTestPass = false;
        }
        if (isJsonOutputEnabled) {
            meanTestResult.put("Name", "Mean");
            meanTestResult.put("PValue", pValue);
            meanTestResult.put("Pass", isTestPass);
            testResults.add(meanTestResult);
        }
        prettyPrintResult("Mean", pValue, testComment);

        HashMap<String, Object> piTestResult = new HashMap<String, Object>();
        test = new Pi();
        pValue = test.getPValue(samples);
        if (pValue > ALPHA) {
            testComment = "PASS.";
            isTestPass = true;
        } else {
            testComment = "FAIL.";
            isTestPass = false;
        }
        if (isJsonOutputEnabled) {
            piTestResult.put("Name", "Pi");
            piTestResult.put("PValue", pValue);
            piTestResult.put("Pass", isTestPass);
            testResults.add(piTestResult);
        }
        prettyPrintResult("Pi", pValue, testComment);
        
        HashMap<String, Object> uncorrelationTestResult = new HashMap<String, Object>();
        test = new UnCorrelation();
        pValue = test.getPValue(samples);
        if (pValue > ALPHA) {
            testComment = "PASS.";
            isTestPass = true;
        } else {
            testComment = "FAIL.";
            isTestPass = false;
        }
        if (isJsonOutputEnabled) {
            uncorrelationTestResult.put("Name", "UnCorrelation");
            uncorrelationTestResult.put("PValue", pValue);
            uncorrelationTestResult.put("Pass", isTestPass);
            testResults.add(uncorrelationTestResult);
        }
        prettyPrintResult("UnCorrelation", pValue, testComment);

        if (isJsonOutputEnabled) {
            runResult.put("Tests", testResults);
            JSONObject runResultJsonObject = new JSONObject(runResult);
            FileWriter file = new FileWriter(outputFilename);
            file.write(runResultJsonObject.toJSONString());
            file.close();
        }
        System.out.println("------------------------------------");
        System.out.println("Finished.");
    }

}
