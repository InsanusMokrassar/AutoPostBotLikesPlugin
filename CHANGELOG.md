# Changelog

## 0.6

* Update Java version `1.7` -> `1.8`
* Fully rewrite system of likes. Now you can
    * Use any count of buttons
    * Use groups
    * Use multichoice/radiochoice
    * Use different messages for mark and unmark

## 0.7

* `AutoPostTelegramBot` version `0.4.3` -> `0.4.4`

## 0.8

* `AutoPostTelegramBot` version `0.4.4` -> `0.4.5`

### 0.8.1

* Add retries count for editing messages and
applying new likes

### 0.8.2

* `AutoPostTelegramBot` version `0.4.5` -> `0.5.0`
* `RatingChangedListener` now work with debounce and
update immediately only first received update per
post

### 0.8.3

* Now `LikesPluginLikesTable` have `DateTime` field
(by default contains `now`). This field will be used
for future updates
* Was added `/attachTargetLike` command and opportunity
to get help by forwarding message from target channel
to bot about enabling of attachment of likes

### 0.8.4

Now plugin always must receive `params` object

### 0.8.5

* Fix of incorrect defaults for `LikesPluginLikesTable`
* Return nullable (not required) parameter for plugin
* Update version of base bot

### 0.8.6

* Fix attach like
* Add refresh message marks

# 1.0.0

* Update build system
* Change version of api library and base library

### 1.0.1

* Fixes and improvements

### 1.0.2

* Hotfix for registered message data

### 1.0.3

* -//-

### 1.0.4

* Update version of bot to `1.0.9`
* Fixes in `debounceByValue`

### 1.0.5

* Rewriting of `RatingChangedListener` to work without recursive calling

### 1.0.6

* Hotfix for `RatingChangedListener`

### 1.0.7

* `RatingChangedListener` now use `executeUnsage`
* Hotfix for `RatingChangedListener

### 1.0.8

* Fix for attach and refresh likes listeners

## 1.1.0

* Update dependencies
* Update logic of work of `RatingChangedListener`: now by default first update for a long time will
be displayed urgent, but all next updates in one time will be delayed with `debounceDelay`

### 1.1.1

* Update version of bot `1.2.0` - `1.2.6`
* Rewrite system of registering for messages and posts likes groups
* Databases channels now have capacity `Channel.CONFLATED`
* Added command `/attachSeparatedLikes`

## 1.2.0

* `kotlin` version `1.3.21` -> `1.3.30`
* `kotlin coroutines` version `1.1.1` -> `1.2.0`
* `kotlin serialization` version `0.10.0` -> `0.11.0`
* `AutoPostTelegramBot` version `1.2.6` -> `1.2.7`
