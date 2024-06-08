# ItemLocker

A class to simplify locking of multiple items.
Useful when you need to enforce multiple exclusion of individual items, but otherwise allow different items to be processed concurrently.

## Usage

```kotlin
val locker = ItemLocker<String>()

fun process(String item) {
  locker.locked(item) {    
    // Here you can safely process the item, knowing other threads won't do it concurrently.
    someInternalProcess(item)
  }
}
```
