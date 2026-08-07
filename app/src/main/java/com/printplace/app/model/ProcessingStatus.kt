package com.printplace.app.model

/**
 * Where an imported model sits in the pipeline described in the architecture
 * doc (importer -> internal mesh -> renderable model). Milestone 1 only ever
 * produces [COPYING], [IMPORTED] and [FAILED]; [PROCESSING] and [READY] are
 * reserved for the STL/3MF geometry importers landing in later milestones.
 */
enum class ProcessingStatus {
    COPYING,
    IMPORTED,
    PROCESSING,
    READY,
    FAILED,
}
