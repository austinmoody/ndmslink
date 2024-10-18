# NDMS Link - CLI Tools

There are some cli tools in this module which may be useful in the running of the NDMS Link project.

Most of these are coming forward from [THSALink](https://github.com/AudaciousInquiry/THSAlink).  Effort will be made to document all tools but initially only those that have been added or edited as part of the NDMS project may be documented here.

## Store Epic Totals Data

In order to aggregate a full MeasureReport for a facility we need to know the total number of beds (overall and by type) for the facility.

At this time there was no way to gather this data from an EPIC FHIR endpoint.  So we need to get this data in a more manual way and have it stored.  So that when the MeasureReport aggregation happens for a facility it can be pulled and used.

The first quick tool created to facilitate this is a shell command `epic-totals-manual`.

This is used to create or update a MeasureReport to store totals for an EPIC facility.  Ultimately the API can pull this report in order to provide totals sections by bed types into the final MeasureReport.  On its own, all we can do using the EPIC FHIR endpoint is to calculate the number of beds occupied by type.  However, we can't determine the total beds by type or the total beds available by type (total - occuypied).

Configuration can be done via the cli-config.yml, environment variables, or a combination of both.  These are the fields required for the configuration:

- Profile URL
- Measure URL
- Subject Identifier
- Measure Report ID
- Data Store Configuration, consisting of
  - Base URL
  - Username
  - Password
  - Socket Timeout

You specify one or more _data sets_ consisting of five (5) pieces of data:

- Group Code
- Group System
- Population Code
- Population System
- Population Count


