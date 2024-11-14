# NDMS Link

Clone of [THSALink](https://github.com/AudaciousInquiry/THSAlink)

## GIS/Dashboard Notes

Current Dashboard setup requires that the aggregated MeasureReport have their Groups/Population in a specific order.

Group Order: "CC", "MM-SS", "MP", "SBN", "MC", "PICU", "NPU", "Beds"

Then inside each group order by Total, Occupied, Available

This order, at this time, needs to be maintained in order for the dashboard to work properly.

At this time there is a MeasureReportSort utility class in ndms to order at the group level.  Need to put something in place to make sure the population inside the group is also sorted properly every time.
