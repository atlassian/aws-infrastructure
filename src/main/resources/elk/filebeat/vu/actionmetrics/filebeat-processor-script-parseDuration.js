function parseDurationToMs(PT) {
    var output = [];
    var durationInSec = 0.0;
    var matches = PT.match(/P(?:(\d*)Y)?(?:(\d*)M)?(?:(\d*)W)?(?:(\d*)D)?T?(?:(\d*)H)?(?:(\d*)M)?(?:([\d\.]*)S)?/i);
    var parts = [
        { // years
            pos: 1,
            multiplier: 86400 * 365
        },
        { // months
            pos: 2,
            multiplier: 86400 * 30
        },
        { // weeks
            pos: 3,
            multiplier: 604800
        },
        { // days
            pos: 4,
            multiplier: 86400
        },
        { // hours
            pos: 5,
            multiplier: 3600
        },
        { // minutes
            pos: 6,
            multiplier: 60
        },
        { // seconds
            pos: 7,
            multiplier: 1
        }
    ];

    for (var i = 0; i < parts.length; i++) {
        if (typeof matches[parts[i].pos] != 'undefined') {
            durationInSec += parseFloat(matches[parts[i].pos]) * parts[i].multiplier;
        }
    }
    var durationInMs = durationInSec * 1000;
    return durationInMs
};

function process(event) {
    var durationString = event.Get("actionMetric.duration");

    event.Put("actionMetric.duration-ms", parseDurationToMs(durationString))
}


/*
To test the logic uncomment this block and run

$ node parseDuration.js

console.log(parseDurationToSeconds("PT2M10.5S"));
*/
