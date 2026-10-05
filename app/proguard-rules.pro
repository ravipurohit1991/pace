# Pace keeps no reflection-based application model. Library consumer rules are
# supplied by Room, DataStore, WorkManager, and Glance.

# Except protobuf-lite, whose generated messages (the DataStore preferences and widget snapshot)
# describe their own schema by field *name*: GeneratedMessageLite looks up "schemaVersion_" and
# friends reflectively when the class first loads. R8 renames those fields unless kept, and every
# minified build then dies on launch with "Field schemaVersion_ ... not found" while debug builds
# work. The library's own consumer rules are not being applied, so the rule lives here.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
