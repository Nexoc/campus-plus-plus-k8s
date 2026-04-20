# Module: studyprograms

## Current Role

Serves study program catalog data and admin CRUD endpoints.

This module is one of the main public data entry points for the frontend.

## Public API

- `GET /api/public/study-programs`
- `GET /api/public/study-programs/{id}`
- `GET /api/public/study-programs/{id}/details`

The list endpoint supports filtering and pagination in the controller.

## Admin API

- `POST /api/study-programs`
- `PUT /api/study-programs/{id}`
- `DELETE /api/study-programs/{id}`

## Notes

- importer-populated study program data is exposed through this module
- course relations are resolved together with study program data
