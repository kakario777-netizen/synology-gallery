# 줄 번호를 남겨 두면 튕김 기록에서 원인 위치를 찾기 쉬움
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# 지도
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# 네트워크 라이브러리 선택 의존성
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 앱 업데이트 수신기 등 매니페스트 등록 클래스
-keep class com.kakario.synogallery.update.** { *; }
