rootProject.name = "mocknest-serverless"

// Software modules following clean architecture
include(":software:domain")
include(":software:application")
include(":software:infra:generation-core")
include(":software:infra:aws:core")
include(":software:infra:aws:runtime")
include(":software:infra:aws:generation")
include(":software:infra:aws:mocknest")

// Deployment configuration
include(":deployment:aws")