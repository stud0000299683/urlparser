package com.utmn.chamortsev.urlparser.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestController {

    @GetMapping("/loadtest-dashboard")
    public String loadTestDashboard() {
        return "redirect:/loadtest.html";
    }

    @GetMapping("/test-data")
    public String testDataPage() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Test Data Generator</title>
                <style>
                    body { font-family: Arial; padding: 20px; }
                    .btn { padding: 10px 20px; background: #007bff; color: white; border: none; cursor: pointer; margin: 5px; }
                </style>
            </head>
            <body>
                <h1>Test Data Generator</h1>
                <button class="btn" onclick="generateData()">Generate 50 Test URLs</button>
                <button class="btn" onclick="startQuickTest()">Start Quick Load Test</button>
                <div id="result"></div>
                <script>
                    async function generateData() {
                        const res = await fetch('/api/loadtest/generate-urls?count=50', { method: 'POST' });
                        const data = await res.json();
                        document.getElementById('result').innerHTML = 
                            '<pre>' + JSON.stringify(data, null, 2) + '</pre>';
                    }
                    async function startQuickTest() {
                        const res = await fetch('/api/loadtest/quick-start?testType=ASYNC', { method: 'POST' });
                        const data = await res.json();
                        document.getElementById('result').innerHTML = 
                            '<pre>' + JSON.stringify(data, null, 2) + '</pre>';
                    }
                </script>
            </body>
            </html>
            """;
    }
}