# Azure APIM mock policy — simulating the Libra `confirmedHearing` response

Test-only mock for the Libra/APIM integration (`LibraClient`/`cp.libra.base-url`), used while the
real Libra/APIM policy doesn't exist yet. **Test environment only — do not apply to a prod APIM
instance.** Mirrors the same idea as `cpp-context-staging-dvla`'s own mock policy
(`stagingdvla-azure-functions/.../DVLA Driver Enquiry/MockFindAPIMPolicy.txt`), which stands in for
a real backend the same way.

## Where this goes

`LibraClient` only checks the HTTP status code (`.retrieve().toBodilessEntity()` — it never reads
the response body), so the mock only needs to control the status.

**Important**: don't add this as a new operation inside the existing `CppGatewayService` API in
APIM — that API is labelled "SOAP" because it was imported from `CPPSoapGateway.wsdl` as a SOAP
pass-through API (its operations are auto-generated from the WSDL's `<portType>`, e.g.
`searchType`/`createFineAccounts` — see `GobClient` in `cpp-context-staging-enforcement`). It's the
wrong container for a hand-written JSON REST operation.

Instead: create a **separate, new API** in the test APIM instance (type: HTTP, blank or import
`api-cp-crime-results-enforcementgateway`'s draft OpenAPI spec), add a `POST /confirmedHearing`
operation to it, and paste the policy below into that operation's **inbound** processing (Azure
Portal → APIs → your new API → the `POST /confirmedHearing` operation → Design tab → the `</>`
code-editor icon on the Inbound processing box).

## Which variant to use

- **Variant A (header-driven)** — for direct curl/Postman testing against the API, where you
  control the request headers yourself.
- **Variant B (content-driven)** — for QA testing through the real UI. The real backend
  (`LibraClient`) builds the request itself; QA has no way to add a custom header from the UI.
  Variant B branches on a field already inside the `confirmedHearing` payload instead, so QA can
  choose the simulated outcome purely by which test data they use - no header, no manual policy
  edits between test runs.

## Variant A: header-driven (direct API testing)

Controlled by an optional request header, `X-Mock-Response-Code`:
- absent, or `202` → 202 Accepted (default — matches "expect 200/202")
- `200` → 200 OK
- any 4xx value (e.g. `400`, `422`) → that status, with a plausible JSON error body

```xml
<policies>
    <inbound>
        <base />
        <!--
            Test-only mock, standing in for the real Libra/APIM policy until it exists.
            Controlled by an optional request header, X-Mock-Response-Code:
              - absent, or "202"  -> 202 Accepted (default - matches "expect 200/202")
              - "200"             -> 200 OK
              - any 4xx value (e.g. "400", "422")  -> that status, with a plausible error body
            Remove this whole policy once the real APIM->Libra integration exists.
        -->
        <choose>
            <when condition="@(context.Request.Headers.GetValueOrDefault("X-Mock-Response-Code", "202") == "202")">
                <return-response>
                    <set-status code="202" reason="Accepted" />
                </return-response>
            </when>
            <when condition="@(context.Request.Headers.GetValueOrDefault("X-Mock-Response-Code", "202") == "200")">
                <return-response>
                    <set-status code="200" reason="OK" />
                </return-response>
            </when>
            <otherwise>
                <return-response>
                    <set-status code="@(int.Parse(context.Request.Headers.GetValueOrDefault("X-Mock-Response-Code", "400")))"
                                reason="Simulated error" />
                    <set-header name="Content-Type" exists-action="override">
                        <value>application/json</value>
                    </set-header>
                    <set-body>@{
                        return new JObject(
                            new JProperty("error", "Simulated failure via X-Mock-Response-Code"),
                            new JProperty("status", context.Request.Headers.GetValueOrDefault("X-Mock-Response-Code", "400"))
                        ).ToString();
                    }</set-body>
                </return-response>
            </otherwise>
        </choose>
    </inbound>
    <backend>
        <base />
    </backend>
    <outbound>
        <base />
    </outbound>
    <on-error>
        <base />
    </on-error>
</policies>
```

### Driving Variant A

```bash
# Success - 202 (default, no header needed)
curl -i -X POST https://<your-test-apim-host>/confirmedHearing \
  -H "Content-Type: application/json" \
  -H "Ocp-Apim-Subscription-Key: <key>" \
  -d '{"caseUrn":"12GD3456789","courtHearingLocation":"B01LY","dateOfHearing":"2026-08-13","timeOfHearing":"10:00"}'

# Success - 200
curl -i -X POST https://<your-test-apim-host>/confirmedHearing \
  -H "X-Mock-Response-Code: 200" \
  -H "Content-Type: application/json" \
  -H "Ocp-Apim-Subscription-Key: <key>" \
  -d '{"caseUrn":"12GD3456789","courtHearingLocation":"B01LY","dateOfHearing":"2026-08-13","timeOfHearing":"10:00"}'

# Simulated error - any 4xx, exercises LibraClient.confirmHearing()'s failure path
curl -i -X POST https://<your-test-apim-host>/confirmedHearing \
  -H "X-Mock-Response-Code: 422" \
  -H "Content-Type: application/json" \
  -H "Ocp-Apim-Subscription-Key: <key>" \
  -d '{"caseUrn":"12GD3456789","courtHearingLocation":"B01LY","dateOfHearing":"2026-08-13","timeOfHearing":"10:00"}'
```

Notes:
- `int.Parse(...)` in the `otherwise` branch requires the header value to be a bare integer
  (`400`, not `4xx`) — anything non-numeric will throw inside the policy.

## Variant B: content-driven (QA testing through the UI)

Branches on `courtHearingLocation` (the court centre/OU code in the `confirmedHearing` payload)
instead of a header. QA chooses the simulated outcome by which court centre they allocate their
test hearing to through the UI — no header, no manual policy edit between scenarios:

- `courtHearingLocation` is a normal, real OU code → 202 Accepted (the default/happy path)
- `courtHearingLocation` == `TEST400` → simulated 400
- `courtHearingLocation` == `TEST422` → simulated 422
- (add more `<when>` blocks the same way for other codes as needed)

**Open question, needs confirming with QA/whoever owns test-data setup before relying on this**:
does the enforcement-hearing-allocation UI actually let QA freely pick which court centre a test
hearing is allocated to, from a list that could include a couple of reserved "trigger" OU codes
(`TEST400`/`TEST422`)? If not — e.g. if the court centre is assigned automatically rather than
chosen — branching on `caseUrn` instead might work, but case references usually follow a strict
validated format, so an artificial "magic" URN suffix might get rejected before the case (and this
whole flow) is even created. Whichever field turns out to be freely QA-controllable, the same
`<choose>`/`<when>` shape below applies - just swap which JSON property is read.

```xml
<policies>
    <inbound>
        <base />
        <!--
            Test-only mock, standing in for the real Libra/APIM policy until it exists.
            Branches on courtHearingLocation (not a header - the real UI-driven flow can't set
            one) so QA can choose the simulated outcome purely through which court centre they
            allocate their test hearing to. Maintain the OU-code -> response mapping in a shared
            QA test-data note, not just this comment - it'll drift if it's not written down
            somewhere QA can see. Remove this whole policy once the real APIM->Libra integration
            exists.
        -->
        <set-variable name="requestBody" value="@(context.Request.Body.As<JObject>(preserveContent: true))" />
        <set-variable name="ouCode" value="@(((JObject)context.Variables["requestBody"])["courtHearingLocation"]?.ToString() ?? "")" />
        <choose>
            <when condition="@(((string)context.Variables["ouCode"]) == "TEST400")">
                <return-response>
                    <set-status code="400" reason="Simulated error" />
                    <set-header name="Content-Type" exists-action="override">
                        <value>application/json</value>
                    </set-header>
                    <set-body>{"error": "Simulated 400 - courtHearingLocation matched test trigger TEST400"}</set-body>
                </return-response>
            </when>
            <when condition="@(((string)context.Variables["ouCode"]) == "TEST422")">
                <return-response>
                    <set-status code="422" reason="Simulated error" />
                    <set-header name="Content-Type" exists-action="override">
                        <value>application/json</value>
                    </set-header>
                    <set-body>{"error": "Simulated 422 - courtHearingLocation matched test trigger TEST422"}</set-body>
                </return-response>
            </when>
            <otherwise>
                <return-response>
                    <set-status code="202" reason="Accepted" />
                </return-response>
            </otherwise>
        </choose>
    </inbound>
    <backend>
        <base />
    </backend>
    <outbound>
        <base />
    </outbound>
    <on-error>
        <base />
    </on-error>
</policies>
```

## Fallback: single global switch (if neither field is QA-controllable)

If it turns out QA can't freely choose either `courtHearingLocation` or `caseUrn`, the next-best
option (still better than manually editing/re-saving this whole policy each time) is an APIM
**Named Value** the policy reads instead of a hardcoded status - e.g. a Named Value
`libra-mock-status-code` (default `202`), referenced in the policy as `{{libra-mock-status-code}}`
inside `<set-status code="{{libra-mock-status-code}}" ...>`. Changing the simulated outcome then
means editing one value in the APIM portal's "Named values" blade - no XML, no redeploying the
operation - at the cost of only one mode being active for every call at a time (can't run a success
scenario and a failure scenario in parallel this way).

## Notes

- Remove this whole policy (or delete the temporary API/operation) once the real Libra/APIM
  integration exists, so it doesn't get mistaken for real behaviour later.
- Point `cp.libra.base-url` (in `service-cp-crime-results-enforcementgateway`'s config) at this
  test APIM API's URL to exercise `LibraClient` end-to-end against it.
