# Build&Sit plugin
### Before we begin...
This plugin makes alternative game mode in mindustry.
### Main description
There exists 4 difficulties + customization.<br>
You have __radius__ near core's, in which you can build.<br>
You have __time__ to build.<br>
You have __limited__ resources.<br>
`Core filled at start`<br>
`Than resource type expensive, than less you will get this resource type.`
### Difficulties
1) Resources<br>
2) Radius for building<br>
3) Time for building in seconds<br>
4) Pings about time every * second(s)<br>
5) Waves every * seconds after time is up<br>
```
   EASY - 20000, 120, 360, 30, 120
 MEDIUM - 10000,  80, 300, 30,  60
   HARD -  5000,  60, 120, 30,  30
EXTREME -  1000,  30,  60, 10,  10
```
You can define __custom__ dfficulty, like:<br>
`CUSTOM#100000, 9999, 1, 1000`<br>
### Commands
This plugin contains this commands: sdifficulty, smode, smix<br>
#### sdifficulty
Change difficulty: `sdifficulty <type>`<br>
Info about current difficulty: `sdifficulty`
#### smode
Enable Build&Sit mode: `smode on`<br>
Alias to smix 100<br>
Disable Build&Sit mode: `smode off`<br>
Alias to smix 0<br>
Check plugin enabled/disabled: `smode`<br>
#### smix
This command allows set you chance of Build&Sit mode
Set chance: `smix <chance>`<br>
Check chance: `smix`
### Known bugs, which i can't fix
Default conveyors doesn't apply configuration which was before it breaked after time is up.
Way to avoid: Use titanium conveyors