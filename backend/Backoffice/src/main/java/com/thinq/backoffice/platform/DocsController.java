package com.thinq.backoffice.platform;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Swagger UI, served entirely from this host.
 *
 * THERE IS NO SIGN-IN BAR, and its absence is the point. This service authenticates to TechExcel
 * on its own behalf, from a credential held in the environment; a caller neither presents one nor
 * can obtain one. A login control on this page would advertise a flow that does not exist and
 * would invite somebody to paste a real back-office credential into a browser.
 *
 * The CSS and JS come from src/main/resources/static/docs/ rather than a CDN: documentation that
 * needs internet fails exactly when it is most wanted. DocsPageTest asserts the page references no
 * external host, so a well-meaning CDN link cannot creep back in.
 */
@Controller
public class DocsController {

    private final GatewayProperties props;

    DocsController(GatewayProperties props) {
        this.props = props;
    }

    @GetMapping(path = {"/", "/docs", VendorGateway.PREFIX + "/docs"},
                produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String docs() {
        String modeChip = props.live() ? "live" : "mock";
        String modeLabel = props.live() ? "LIVE" : "MOCK";
        String modeHint = props.live()
                ? "Calls reach the real back office at " + props.baseUrl()
                : "Generated data. Nothing leaves this host.";
        return """
               <!doctype html>
               <html lang="en">
               <head>
                 <meta charset="utf-8">
                 <meta name="viewport" content="width=device-width, initial-scale=1">
                 <title>FMS Back Office API</title>
                 <link rel="stylesheet" href="/docs/swagger-ui.css">
                 <style>
                   body { margin: 0; }
                   #modebar {
                     display: flex; flex-wrap: wrap; gap: 10px; align-items: center;
                     padding: 12px 20px; border-bottom: 1px solid #DCE4DD;
                     background: #FBFCFA; color: #16201B;
                     font: 14px/1.5 "IBM Plex Sans", system-ui, sans-serif;
                   }
                   #modebar .chip {
                     font: 600 11.5px/1 ui-monospace, Menlo, monospace;
                     letter-spacing: .07em; padding: 5px 8px; border-radius: 2px;
                   }
                   #modebar .chip.mock { background: #E8F1EC; color: #0B6E4F; border: 1px solid #0B6E4F; }
                   #modebar .chip.live { background: #F6E9E5; color: #A4341F; border: 1px solid #A4341F; }
                   #modebar .hint { color: #5B6A61; margin-right: auto; }
                   :focus-visible { outline: 2px solid #0B6E4F; outline-offset: 2px; }
                 </style>
               </head>
               <body>
                 <div id="modebar">
                   <span class="chip {{MODE_CHIP}}">{{MODE_LABEL}}</span>
                   <span class="hint">{{MODE_HINT}}</span>
                 </div>
                 <div id="swagger"></div>
                 <script src="/docs/swagger-ui-bundle.js"></script>
                 <script>
                   const ui = SwaggerUIBundle({
                     url: "/openapi.yaml",
                     dom_id: "#swagger",
                     deepLinking: true,
                     validatorUrl: null,
                     docExpand: "list"
                   });

                 </script>
               </body>
               </html>
               """
                .replace("{{MODE_CHIP}}", modeChip)
                .replace("{{MODE_LABEL}}", modeLabel)
                .replace("{{MODE_HINT}}", modeHint);
    }
}
