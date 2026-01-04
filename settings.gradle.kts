rootProject.name = "mocknest-serverless"

// Software modules following clean architecture
include(":software:domain")
include(":software:application")
include(":software:infra:aws")

// Deployment configuration
include(":deployment:aws")