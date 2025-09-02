package com.www.goodjob.util;

import org.hibernate.usertype.UserTypeLegacyBridge;

import java.text.Format;

public class CaludeFeedbackFormater {

    private  final  String html="""
          <meta charset="utf-8"/>
          <div>
           %s
           %s
          </div> 
          </meta>
    """;

    private final String css ="""
              <style>
                     :root {
                       font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Noto Sans KR', 'Apple SD Gothic Neo', sans-serif;
                       --bg-primary: #f8fafc;
                       --bg-secondary: #ffffff;
                       --bg-card: #f1f5f9;
                       --border-color: #e2e8f0;
                       --text-primary: #0f172a;
                       --text-secondary: #334155;
                       --text-muted: #64748b;
                       --accent-good: #16a34a;
                       --accent-bad: #dc2626;
                       --accent-tip: #2563eb;
                       --accent-good-bg: rgba(22, 163, 74, 0.1);
                       --accent-bad-bg: rgba(220, 38, 38, 0.1);
                       --accent-tip-bg: rgba(37, 99, 235, 0.1);
                     }
                
                     * {
                       box-sizing: border-box;
                     }
                
                     body {
                       margin: 0;
                       background: var(--bg-primary);
                       color: var(--text-primary);
                       line-height: 1.6;
                     }
                
                     .wrap {
                       max-width: 1200px;
                       margin: 40px auto;
                       padding: 32px;
                       background: var(--bg-secondary);
                       border-radius: 20px;
                       box-shadow: 0 20px 40px rgba(0,0,0,.1);
                       border: 1px solid var(--border-color);
                     }
                
                     h1 {
                       margin: 0 0 16px;
                       font-size: 32px;
                       font-weight: 700;
                       background: linear-gradient(135deg, #2563eb, #7c3aed);
                       -webkit-background-clip: text;
                       -webkit-text-fill-color: transparent;
                       background-clip: text;
                     }
                
                     .meta {
                       color: var(--text-muted);
                       font-size: 14px;
                       margin-bottom: 32px;
                       padding: 12px 16px;
                       background: var(--bg-card);
                       border-radius: 10px;
                       border-left: 4px solid #2563eb;
                     }
                
                     .grid {
                       display: grid;
                       grid-template-columns: 1fr;
                       gap: 20px;
                     }
                
                     @media (min-width: 1000px) {
                       .grid { grid-template-columns: 1fr 1fr; }
                     }
                
                     .card {
                       background: var(--bg-card);
                       border: 1px solid var(--border-color);
                       border-radius: 16px;
                       padding: 24px;
                       transition: all 0.3s ease;
                     }
                
                     .card:hover {
                       border-color: #2563eb;
                       transform: translateY(-2px);
                       box-shadow: 0 8px 25px rgba(37, 99, 235, 0.15);
                     }
                
                     .card h2 {
                       margin-top: 0;
                       margin-bottom: 16px;
                       font-size: 18px;
                       font-weight: 600;
                       color: var(--text-secondary);
                     }
                
                     .mono {
                       white-space: pre-wrap;
                       font-family: 'JetBrains Mono', 'Fira Code', Consolas, 'Courier New', monospace;
                       line-height: 1.7;
                       font-size: 13px;
                       background: rgba(0,0,0,0.05);
                       padding: 20px;
                       border-radius: 12px;
                       border: 1px solid var(--border-color);
                     }
                
                     .feedback {
                       background: var(--bg-card);
                       border: 1px solid var(--border-color);
                       padding: 32px;
                       border-radius: 16px;
                       margin-bottom: 32px;
                     }
                
                     .feedback h2 {
                       margin-top: 0;
                       font-size: 24px;
                       font-weight: 700;
                       color: var(--text-primary);
                       margin-bottom: 24px;
                     }
                
                     .good-things, .bad-things, .tips {
                       margin-bottom: 32px;
                       padding: 24px;
                       border-radius: 12px;
                       border-left: 5px solid;
                     }
                
                     .good-things {
                       background: var(--accent-good-bg);
                       border-left-color: var(--accent-good);
                     }
                
                     .bad-things {
                       background: var(--accent-bad-bg);
                       border-left-color: var(--accent-bad);
                     }
                
                     .tips {
                       background: var(--accent-tip-bg);
                       border-left-color: var(--accent-tip);
                     }
                
                     .good-things h2 {
                       color: var(--accent-good);
                       font-size: 20px;
                       margin-bottom: 16px;
                       display: flex;
                       align-items: center;
                       gap: 8px;
                     }
                
                     .good-things h2::before {
                       content: "✓";
                       font-size: 24px;
                       font-weight: bold;
                     }
                
                     .bad-things h2 {
                       color: var(--accent-bad);
                       font-size: 20px;
                       margin-bottom: 16px;
                       display: flex;
                       align-items: center;
                       gap: 8px;
                     }
                
                     .bad-things h2::before {
                       content: "⚠";
                       font-size: 24px;
                     }
                
                     .tips h2 {
                       color: var(--accent-tip);
                       font-size: 20px;
                       margin-bottom: 16px;
                       display: flex;
                       align-items: center;
                       gap: 8px;
                     }
                
                     .tips h2::before {
                       content: "💡";
                       font-size: 24px;
                     }
                
                     .good-things ul,
                     .bad-things ul,
                     .tips ul {
                       list-style: none;
                       padding: 0;
                       margin: 0;
                     }
                
                     .good-things li,
                     .bad-things li,
                     .tips li {
                       margin-bottom: 16px;
                       padding: 16px;
                       background: rgba(0,0,0,0.03);
                       border-radius: 8px;
                       border-left: 3px solid;
                       line-height: 1.7;
                     }
                
                     .good-things li {
                       border-left-color: var(--accent-good);
                     }
                
                     .bad-things li {
                       border-left-color: var(--accent-bad);
                     }
                
                     .tips li {
                       border-left-color: var(--accent-tip);
                     }
                
                     strong {
                       color: #c2410c;
                       font-weight: 600;
                     }
                
                     .section-divider {
                       height: 1px;
                       background: linear-gradient(90deg, transparent, var(--border-color), transparent);
                       margin: 40px 0;
                     }
                
                     @media (max-width: 768px) {
                       .wrap {
                         margin: 20px;
                         padding: 24px;
                       }
                
                       h1 {
                         font-size: 28px;
                       }
                
                       .card {
                         padding: 20px;
                       }
                
                       .feedback {
                         padding: 24px;
                       }
                
                       .mono {
                         font-size: 12px;
                         padding: 16px;
                       }
                     }
                   </style>
            """;

    public String  format(String feedBack){
        return this.html.formatted(
                this.css,
                feedBack
        ).replaceAll("[\\n\\r\\t]", "");
    }


}
