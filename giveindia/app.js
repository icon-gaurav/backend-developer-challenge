var createError = require('http-errors');
var express = require('express');
var path = require('path');
var cookieParser = require('cookie-parser');
var logger = require('morgan');
var formidable = require('formidable');
var request = require('request');
const csv = require('csv-parser');
var fs = require('fs');
const createCSVWriter = require('csv-writer').createObjectCsvWriter;

var app = express();

// view engine setup
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'pug');

app.use(logger('dev'));
app.use(express.json());
app.use(express.urlencoded({extended: false}));
app.use(cookieParser());
app.use(express.static(path.join(__dirname, 'public')));

app.post('/report', (req, res, next) => {
    var form = new formidable.IncomingForm();
    form.parse(req, (err, fields, files) => {
        if (err) {
            next(createError(400));
        }
        if (files) {
            request(`https://api.exchangerate-api.com/v4/latest/${fields.curr}`, {json: true}, (err, result, body) => {
                if (err) {
                    next(createError(500));
                } else {
                    let entries = [];
                    let discard = [];
                    let nonprofits = [];

                    fs.createReadStream(files.csv_file.path)
                        .pipe(csv())
                        .on('data', (data) => {
                            if (body.rates[data['Donation Currency']] != undefined && data.Nonprofit != "") {
                                if (data['Donation Amount'] == "") {
                                    data['Donation Amount'] = 0;
                                } else {
                                    data['Donation Amount'] = parseFloat(data['Donation Amount']) / body.rates[data['Donation Currency']];
                                }
                                if (data.Fee == "") {
                                    data.Fee = 0;

                                } else {
                                    data.Fee = parseFloat(data['Fee']) / body.rates[data['Donation Currency']];
                                }

                                if (entries[data.Nonprofit]) {
                                    entries[data.Nonprofit]['amount'] += data['Donation Amount'];
                                    entries[data.Nonprofit]['fee'] += data['Fee'];
                                    entries[data.Nonprofit]['count'] += 1;
                                } else {
                                    let entry = {
                                        'nonprofit': data.Nonprofit,
                                        'amount': data['Donation Amount'],
                                        'fee': data['Fee'],
                                        'count': 1
                                    };
                                    nonprofits.push(data.Nonprofit);
                                    entries[data.Nonprofit] = entry;
                                }
                            } else {
                                discard.push(data);
                            }

                        })
                        .on('end', () => {
                            let filename = "/uploads/" + (new Date().getTime());
                            let reportWriter = createCSVWriter({
                                path: __dirname + "/public" + filename + "report.csv",
                                header: [
                                    {id: 'nonprofit', title: 'Nonprofit'},
                                    {id: 'amount', title: 'Total Amount'},
                                    {id: 'fee', title: 'Total Fee'},
                                    {id: 'count', title: 'Number of Donations'},
                                ]
                            });

                            let report = [];
                            for (let key = 0; key < nonprofits.length; key++) {
                                report.push(entries[nonprofits[key]]);
                            }
                            console.log(report);

                            reportWriter.writeRecords(report)
                                .then(() => {
                                    createCSVWriter({
                                        path: __dirname + "/public" + filename + "discardreport.csv",
                                        header: ['Date', 'Order Id', 'Nonprofit', 'Donation Currency', 'Donation Amount', 'Fee']
                                    })
                                        .writeRecords(discard)
                                        .then(() => console.log("done"))
                                        .catch(err => {
                                            console.log(err)
                                            next(createError(500));
                                        })
                                    res.render('report', {
                                        title: 'Give India Backend Challenge',
                                        report: filename,
                                        error: false
                                    })
                                })
                                .catch(err => {
                                    console.log(err)
                                    next(createError(500));
                                })
                        })
                }

            })
        } else {
            res.render('report', {
                title: 'Give India Backend Challenge',
                error: true
            })
        }

    })

})

app.get('/', (req, res, next) => {
    res.render('index', {title: 'Give India Backend Challenge'});
});


// catch 404 and forward to error handler
app.use(function (req, res, next) {
    next(createError(404));
});

// error handler
app.use(function (err, req, res, next) {
    // set locals, only providing error in development
    res.locals.message = err.message;
    res.locals.error = req.app.get('env') === 'development' ? err : {};

    // render the error page
    res.status(err.status || 500);
    res.render('error');
});

module.exports = app;
