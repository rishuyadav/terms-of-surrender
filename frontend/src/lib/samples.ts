import type { ContractContext } from "./types";

export interface Sample {
  id: string;
  label: string;
  description: string;
  context: ContractContext;
  text: string;
}

// Original fictional examples, not assertions about any real company or applicable law.
export const samples: Sample[] = [
  {
    id: "gym",
    label: "The gym membership",
    description: "US · Consumer agreement",
    context: { jurisdiction: "US", category: "CONSUMER", region: "" },
    text: `EVERGREEN FITNESS — SAMPLE MEMBERSHIP AGREEMENT
Fictional demonstration document. Not legal advice.

1. Membership and payment
The membership costs $45 per month and begins on the date signed. The initial term is twelve months. An additional fee of $60 is charged on each membership anniversary.

2. Renewal and cancellation
The membership renews automatically for another twelve-month term unless the club receives written cancellation notice at least thirty days before the current term ends. Early cancellation during a term carries an early termination fee equal to two monthly payments. No early termination fee applies if the club closes permanently.

3. Disputes
Disputes will be resolved by individual binding arbitration rather than a court trial, except for claims eligible for small claims court. Members may opt out of arbitration by sending written notice to the club within thirty days of signing. The notice procedure and address appear in the welcome pack, which is not included in this sample.

4. Changes
The club may change these terms on thirty days' written notice. Members may reject a material price increase by cancelling before it takes effect without an early termination fee.

5. Publicity
The member grants the club permission to use event photographs containing their likeness for marketing purposes. This permission can be withdrawn for future uses by writing to the club.

6. Club closure
If the club closes permanently, prepaid amounts for unused membership months will be refunded.`,
  },
  {
    id: "app",
    label: "The app subscription",
    description: "India · Consumer agreement",
    context: { jurisdiction: "INDIA", category: "CONSUMER", region: "" },
    text: `DAYLIGHT NOTES — SAMPLE SUBSCRIPTION TERMS
Fictional demonstration document. Not legal advice.

1. Subscription
The annual subscription costs INR 2,400. Your subscription renews automatically each year unless you cancel through account settings before the renewal date. We will email a renewal reminder seven days before charging you.

2. Refunds
Subscription payments are non-refundable except where a refund is required by applicable law or expressly promised in a written promotion. Cancelling stops the next renewal but does not immediately end access for the paid period.

3. Personal information
We share personal information with third-party hosting and payment providers to operate the service. We do not sell your notes or use their contents for marketing purposes. A separate privacy policy describes retention and deletion; that policy is not included here.

4. Service changes
We may modify these terms on thirty days' notice. A change to the annual price will apply at your next renewal, not during a paid term.

5. Liability
Our liability is limited to subscription fees paid in the preceding twelve months, except where this limit is prohibited by applicable law. This clause does not exclude responsibility for fraud.

6. Your content
You retain ownership of your notes. You grant us a limited license to store and process them only to provide the service.`,
  },
  {
    id: "freelance",
    label: "The freelance contract",
    description: "US · Freelance agreement",
    context: { jurisdiction: "US", category: "FREELANCE", region: "" },
    text: `NORTHSTAR STUDIO — SAMPLE FREELANCE SERVICES AGREEMENT
Fictional demonstration document. Not legal advice.

1. Services
The freelancer will produce a five-page website using the written brief attached to the agreement. That brief is not included in this sample. Revisions are included until the client is satisfied. Work outside the original brief requires a written change request and agreed pricing before the freelancer begins additional work.

2. Payment
The project fee is USD 3,000. Payment is due net 60 from the date of the final invoice, which may be issued upon final acceptance by the client. The agreement does not set a deadline for the client's acceptance decision. Late payments accrue interest at 1.5% per month.

3. Intellectual property
All intellectual property rights in project deliverables transfer to the client upon creation and full payment. Pre-existing tools and materials remain the freelancer's property and are licensed to the client only as needed to use the deliverables. The freelancer may display the work in their portfolio unless the client gives written objection within thirty days of project completion.

4. Indemnity
The freelancer shall indemnify the client against third-party claims arising from the freelancer's infringement of intellectual property rights. This obligation has no stated financial cap and does not apply to materials supplied by the client.

5. Termination
The client may terminate for convenience on seven days' notice. The client will pay for accepted deliverables completed before termination; work in progress that has not yet been accepted will not be compensated unless the parties agree otherwise in writing.

6. Restrictions
During the project and for six months afterwards, the freelancer will not perform competing design services for the client's direct competitors in the same market segment. The parties have not defined direct competitors or market segment in this agreement.`,
  },
  {
    id: "india-freelance",
    label: "The India services contract",
    description: "India · Freelance agreement",
    context: { jurisdiction: "INDIA", category: "FREELANCE", region: "" },
    text: `MERIDIAN TECH SOLUTIONS — SAMPLE SERVICE AGREEMENT
Fictional demonstration document. Not legal advice.

1. Scope of work
The service provider will design and deliver a mobile application interface as described in the statement of work attached to this agreement. That document is not included in this sample. Work outside the agreed scope requires a written change order signed by both parties before work begins.

2. Fees and payment
The total fee is INR 1,50,000 exclusive of applicable GST. The client will pay fifty percent on signing and the balance within net 30 days of final delivery. The service provider may raise an invoice upon delivery; the client's acceptance is deemed granted after seven days of silence. Late payments will attract interest at the rate of twelve percent per annum from the due date.

3. Intellectual property
All rights in deliverables vest in the client upon receipt of full payment under the Copyright Act 1957. The service provider retains ownership of any pre-existing tools, libraries, and frameworks; a royalty-free license to use them as part of the deliverables is granted for the duration of the client's use of the application.

4. Confidentiality
The service provider shall treat all client materials, business data, and design specifications as confidential and may not disclose them to third parties. This obligation continues for two years after the agreement ends.

5. Dispute resolution
Any disputes arising out of or in connection with this agreement shall be referred to and finally resolved by arbitration in accordance with the Arbitration and Conciliation Act 1996. The seat of arbitration shall be Bengaluru. The language of the proceedings shall be English.

6. Termination
Either party may terminate this agreement on fourteen days' written notice. On termination, the client will pay for work completed and accepted up to the termination date. Pre-paid amounts for undelivered work will be refunded within thirty days.`,
  },
];
