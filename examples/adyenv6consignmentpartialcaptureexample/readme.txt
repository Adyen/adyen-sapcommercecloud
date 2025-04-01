Example of multiple partial capture

Assumptions:
- Order is being artificially split based on named delivery date - each order entry gets its own delivery date
- Main goal of this example is to introduce multiple partial capture - this is done in AdyenCaptureConsignmentsAction
- adyen-order-process is modified in impex in this extension - it contains 2 extra steps: splitOrder and captureConsignments
- consignment processes are not used
- no change of order status is considered
- discount and delivery cost calculation when splitting order into consignments isn't addressed in this example - only entry total values are summed up

Setup:
- add <extension name="adyenv6consignmentpartialcaptureexample"/> to localextensions.xml
- do ant clean all and system update with check on adyenv6consignmentpartialcaptureexample extension to update dynamic business process