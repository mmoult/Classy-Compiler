; External declaration of the puts function
declare i32 @puts(i8* nocapture) nounwind
; Memory management functions
declare i8* @malloc(i32) nounwind
declare void @free(i8*) nounwind

FUNCTION ..print {
  ret void
}

FUNCTION ..super(%this, %wanted) {
  ; Each class with any parents should ask if %wanted is the
  ;  type of any supers, and return that super if so.
  ret %this
}