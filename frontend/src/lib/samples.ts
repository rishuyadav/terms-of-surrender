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
    description: "Freelance · Change jurisdiction as needed",
    context: { jurisdiction: "UNSPECIFIED", category: "FREELANCE", region: "" },
    text: `NORTHSTAR STUDIO — SAMPLE FREELANCE SERVICES AGREEMENT
Fictional demonstration document. No governing law is specified.

1. Services
The freelancer will produce a five-page website using the written brief attached to the agreement. That brief is not included in this sample. Revisions are included until the client is satisfied. Work outside the original brief requires a written change request and agreed pricing.

2. Payment
The project fee is 3,000 in a currency to be confirmed by the parties. Payment is due net 60 from the date of the final invoice, which may be issued upon final acceptance by the client. The agreement does not set a deadline for the client's acceptance decision.

3. Intellectual property
All intellectual property rights in project deliverables transfer to the client upon creation. Pre-existing tools and materials remain the freelancer's property and are licensed to the client only as needed to use the deliverables.

4. Indemnity
The freelancer shall indemnify the client against third-party claims arising from the freelancer's infringement of intellectual property rights. This obligation has no stated financial cap and does not apply to materials supplied by the client.

5. Termination
The client may terminate for convenience on seven days' notice. The client will pay for accepted deliverables completed before termination; treatment of unaccepted work is not specified.

6. Other clients
During the project and for six months afterwards, the freelancer will not perform competing design services for the client's direct competitors. The parties have not defined direct competitors.`,
  },
];
