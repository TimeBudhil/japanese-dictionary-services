# UPSTREAM DATA SOURCES
                    ====================

       EDRDG / JMdict              KANJIDIC2
             │                         │
             │ XML                     │ XML
             ▼                         ▼
       ┌───────────┐             ┌───────────┐
       │  Fetcher  │             │  Fetcher  │
       └─────┬─────┘             └─────┬─────┘
             │                         │
             └──────────┬──────────────┘
                        ▼
                ┌─────────────────┐
                │   Raw staging   │
                │                 │
                │ version/hash    │
                │ downloaded file │
                └────────┬────────┘
                         │
                         ▼
                ┌─────────────────┐
                │    Importers    │
                │                 │
                │ JMdict parser   │
                │ KANJIDIC parser │
                │ JMnedict parser │
                │ RADKFILE parser │
                └────────┬────────┘
                         │
                         ▼
                ┌─────────────────┐
                │ Normalization / │
                │   enrichment    │
                └────────┬────────┘
                         │
                         ▼
             ┌───────────────────────┐
             │       Jisho DB        │
             │                       │
             │ words                 │
             │ readings              │
             │ senses                │
             │ kanji                 │
             │ names                 │
             │ radicals              │
             │ examples              │
             │ cross-references      │
             └───────────┬───────────┘
                         │
                 ┌───────┴────────┐
                 ▼                ▼
          search indexes      application
          / caches            API/web app
                 │                │
                 └───────┬────────┘
                         ▼
                     jisho.org



# Example pipeline
                 EDRDG
                   │
                   │ new JMdict XML
                   ▼
          ┌──────────────────┐
          │ update detector  │
          └────────┬─────────┘
                   │
                   ▼
          ┌──────────────────┐
          │ download + verify│
          └────────┬─────────┘
                   │
                   ▼
          ┌──────────────────┐
          │ JMdict importer  │
          └────────┬─────────┘
                   │
                   ▼
        ┌───────────────────────┐
        │ staging Jisho database│
        └───────────┬───────────┘
                    │
          ┌─────────┴───────────┐
          ▼                     ▼
   normalize data         derive relationships
          │                     │
          └──────────┬──────────┘
                     ▼
             build search index
                     │
                     ▼
              validation tests
                     │
                     ▼
              publish release
                     │
                     ▼
               jisho.org
