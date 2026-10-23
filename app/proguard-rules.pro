# Names are shortened and classes moved into one package for a smaller, faster DEX.
# Crash reports stay readable with the mapping file attached to each GitHub release
# (Google Play reads it from the AAB): keep line numbers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-repackageclasses

# Enum names are stored in the database, settings and backups (Kind.name / valueOf),
# so they must never change between versions.
-keepclassmembernames enum com.dallet.app.** {
    <fields>;
}
