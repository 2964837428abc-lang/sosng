(() => {
  'use strict';

  const COURSE_KEY = 'ace-coach-courses-v1';
  const SETTINGS_KEY = 'ace-coach-settings-v1';
  const NOTIFIED_KEY = 'ace-coach-notified-v1';
  const $ = id => document.getElementById(id);
  const pad = value => String(value).padStart(2, '0');
  const toDateKey = date => [date.getFullYear(), pad(date.getMonth() + 1), pad(date.getDate())].join('-');
  const atLocalTime = (dateKey, time) => new Date(dateKey + 'T' + time + ':00');
  const addDays = (date, amount) => { const copy = new Date(date); copy.setDate(copy.getDate() + amount); return copy; };
  const uid = () => (crypto.randomUUID ? crypto.randomUUID() : 'ace-' + Date.now() + '-' + Math.random().toString(16).slice(2));
  const escapeHtml = text => String(text ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[char]));
  const durationHours = course => Math.max(0, (atLocalTime(course.date, course.endTime) - atLocalTime(course.date, course.startTime)) / 3600000);
  const courseStart = course => atLocalTime(course.date, course.startTime);
  const courseEnd = course => atLocalTime(course.date, course.endTime);
  const sortCourses = list => [...list].sort((a, b) => courseStart(a) - courseStart(b));

  let courses = loadCourses();
  let settings = loadSettings();
  let scheduleFilter = 'upcoming';
  let nextCourse = null;
  let pendingConfirm = null;

  function loadSettings() {
    try {
      return { reminderMinutes: 120, coachName: '', coachPhone: '', ...JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}') };
    } catch {
      return { reminderMinutes: 120, coachName: '', coachPhone: '' };
    }
  }

  function sampleCourses() {
    const now = new Date();
    const firstDate = now.getHours() < 19 ? new Date(now) : addDays(now, 1);
    const firstHour = now.getHours() < 19 ? Math.max(8, now.getHours() + 2) : 8;
    const secondDate = firstHour <= 19 ? firstDate : addDays(firstDate, 1);
    const secondHour = firstHour <= 19 ? firstHour + 2 : 10;
    const thirdDate = addDays(firstDate, 1);
    return [
      { id: uid(), student:'小宇', date:toDateKey(firstDate), startTime:pad(firstHour)+':00', endTime:pad(Math.min(firstHour+1,23))+':00', type:'一对一私教', location:'星河网球中心 · 3号场', reminderMinutes:120, paid:true, notes:'正手稳定性与脚步训练' },
      { id: uid(), student:'安安', date:toDateKey(secondDate), startTime:pad(secondHour)+':00', endTime:pad(Math.min(secondHour+1,23))+':00', type:'一对一私教', location:'星河网球中心 · 2号场', reminderMinutes:120, paid:false, notes:'发球动作调整' },
      { id: uid(), student:'林先生 / 陈先生', date:toDateKey(thirdDate), startTime:'18:30', endTime:'20:00', type:'双人课', location:'城市运动公园 · 1号场', reminderMinutes:120, paid:false, notes:'' }
    ];
  }

  function loadCourses() {
    const saved = localStorage.getItem(COURSE_KEY);
    if (saved !== null) {
      try { return JSON.parse(saved); } catch { return []; }
    }
    const seeded = sampleCourses();
    localStorage.setItem(COURSE_KEY, JSON.stringify(seeded));
    return seeded;
  }

  function saveCourses() {
    localStorage.setItem(COURSE_KEY, JSON.stringify(courses));
    renderAll();
  }

  function saveSettingsData() {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
  }

  function formatDate(date, options = {}) {
    return new Intl.DateTimeFormat('zh-CN', { month:'long', day:'numeric', weekday:'short', ...options }).format(date);
  }

  function weekStart(date = new Date()) {
    const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = copy.getDay() || 7;
    copy.setDate(copy.getDate() - day + 1);
    return copy;
  }

  function greeting() {
    const hour = new Date().getHours();
    return hour < 6 ? '夜深了' : hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好';
  }

  function renderHeader() {
    const now = new Date();
    $('todayLabel').textContent = '今天 · ' + new Intl.DateTimeFormat('zh-CN', { month:'long', day:'numeric', weekday:'long' }).format(now);
    $('pageTitle').textContent = greeting() + '，' + (settings.coachName || '教练');
    $('coachNameSide').textContent = settings.coachName || '我的课表';
    $('todayScheduleDate').textContent = formatDate(now);
  }

  function renderDashboard() {
    const now = new Date();
    const todayKey = toDateKey(now);
    const today = sortCourses(courses.filter(course => course.date === todayKey));
    const start = weekStart(now);
    const end = addDays(start, 7);
    const weekCourses = courses.filter(course => courseStart(course) >= start && courseStart(course) < end);
    const upcoming = sortCourses(courses.filter(course => courseEnd(course) > now));
    nextCourse = upcoming[0] || null;

    if (nextCourse) {
      $('nextStudent').textContent = nextCourse.student + ' · ' + nextCourse.type;
      $('nextMeta').textContent = formatRelativeDay(nextCourse.date) + ' ' + nextCourse.startTime + '–' + nextCourse.endTime + '  ·  ' + nextCourse.location;
      $('nextCalendarButton').disabled = false;
    } else {
      $('nextStudent').textContent = '暂时没有待上课程';
      $('nextMeta').textContent = '点击“新增课程”，把下一节课安排起来。';
      $('nextCalendarButton').disabled = true;
    }
    updateCountdown();

    const todayDuration = today.reduce((sum, course) => sum + durationHours(course), 0);
    $('todayCount').innerHTML = today.length + ' <em>节</em>';
    $('todayHours').textContent = '共 ' + formatHours(todayDuration) + ' 小时';
    $('weekCount').innerHTML = weekCourses.length + ' <em>节</em>';
    $('weekTrend').textContent = weekCourses.length ? '本周已安排' : '待安排';
    $('unpaidCount').innerHTML = courses.filter(course => !course.paid && courseEnd(course) >= start).length + ' <em>节</em>';

    if (!today.length) {
      $('todayTimeline').innerHTML = '<div class="timeline-empty"><strong>今天还没有课程</strong><span>给自己留点时间，或新增一节课。</span></div>';
    } else {
      const firstUpcomingId = today.find(course => courseEnd(course) > now)?.id;
      $('todayTimeline').innerHTML = today.map(course => {
        const current = course.id === firstUpcomingId ? ' next' : '';
        return '<div class="timeline-item' + current + '">' +
          '<span class="timeline-time">' + escapeHtml(course.startTime) + '</span><i class="timeline-dot"></i>' +
          '<div class="timeline-main"><strong>' + escapeHtml(course.student) + '</strong><small>' + escapeHtml(course.location) + '</small></div>' +
          '<span class="timeline-tag">' + escapeHtml(course.type) + '</span></div>';
      }).join('');
    }
    renderWeekBars(start);
  }

  function renderWeekBars(start) {
    const labels = ['一','二','三','四','五','六','日'];
    const todayKey = toDateKey(new Date());
    const daily = labels.map((label, index) => {
      const date = addDays(start, index);
      const key = toDateKey(date);
      const dayCourses = courses.filter(course => course.date === key);
      return { label, key, count:dayCourses.length, hours:dayCourses.reduce((sum, course) => sum + durationHours(course), 0) };
    });
    const max = Math.max(1, ...daily.map(day => day.hours));
    $('weekBars').innerHTML = daily.map(day => {
      const height = Math.max(5, Math.round((day.hours / max) * 100));
      const isToday = day.key === todayKey;
      return '<div class="bar-column' + (isToday ? ' today' : '') + '"><div class="bar-track"><div class="bar' + (isToday ? ' today' : '') + '" style="height:' + height + '%"><span>' + day.count + '节</span></div></div><small>周' + day.label + '</small></div>';
    }).join('');
    $('weekTotalHours').textContent = formatHours(daily.reduce((sum, day) => sum + day.hours, 0)) + 'h';
  }

  function formatHours(value) {
    return Number.isInteger(value) ? String(value) : value.toFixed(1);
  }

  function formatRelativeDay(key) {
    const today = toDateKey(new Date());
    const tomorrow = toDateKey(addDays(new Date(), 1));
    if (key === today) return '今天';
    if (key === tomorrow) return '明天';
    return formatDate(new Date(key + 'T12:00:00'));
  }

  function updateCountdown() {
    if (!nextCourse) { $('countdown').textContent = '—'; return; }
    const diff = courseStart(nextCourse) - new Date();
    if (diff <= 0) { $('countdown').textContent = '正在上课'; return; }
    const totalMinutes = Math.floor(diff / 60000);
    const days = Math.floor(totalMinutes / 1440);
    const hours = Math.floor((totalMinutes % 1440) / 60);
    const minutes = totalMinutes % 60;
    $('countdown').textContent = days ? days + '天 ' + hours + '小时' : hours ? hours + '小时 ' + minutes + '分' : minutes + '分钟';
  }

  function renderCourseList() {
    const now = new Date();
    const query = $('courseSearch').value.trim().toLowerCase();
    let filtered = courses.filter(course => {
      const matchesTime = scheduleFilter === 'all' || (scheduleFilter === 'upcoming' ? courseEnd(course) >= now : courseEnd(course) < now);
      const haystack = [course.student, course.location, course.type, course.notes].join(' ').toLowerCase();
      return matchesTime && (!query || haystack.includes(query));
    });
    filtered = sortCourses(filtered);
    if (scheduleFilter === 'past') filtered.reverse();
    if (!filtered.length) {
      $('courseList').innerHTML = '<div class="panel list-empty"><strong>没有找到课程</strong><span>换一个筛选条件，或者新增一节课程。</span></div>';
      return;
    }
    const grouped = filtered.reduce((map, course) => {
      (map[course.date] ||= []).push(course);
      return map;
    }, {});
    $('courseList').innerHTML = Object.entries(grouped).map(([date, items]) =>
      '<section class="date-group"><div class="date-group-label">' + escapeHtml(formatRelativeDay(date)) + '</div>' +
      items.map(renderCourseRow).join('') + '</section>'
    ).join('');
  }

  function renderCourseRow(course) {
    const past = courseEnd(course) < new Date();
    return '<article class="course-row">' +
      '<div class="course-time"><strong>' + escapeHtml(course.startTime) + '</strong><small>至 ' + escapeHtml(course.endTime) + '</small></div>' +
      '<div class="course-student"><span class="student-initial">' + escapeHtml(course.student.slice(0,1)) + '</span><span><strong>' + escapeHtml(course.student) + '</strong><small>' + escapeHtml(course.type) + '</small></span></div>' +
      '<div class="course-location"><strong>' + escapeHtml(course.location) + '</strong><small>' + (course.paid ? '<span class="status-badge success">已收课时</span>' : '<span class="status-badge warning">待收课时</span>') + (past ? ' · 已结束' : '') + '</small></div>' +
      '<div class="course-actions"><button class="mini-button" data-action="calendar" data-id="' + course.id + '" title="添加到日历" aria-label="添加到日历">▣</button><button class="mini-button" data-action="edit" data-id="' + course.id + '" title="编辑" aria-label="编辑">✎</button><button class="mini-button" data-action="delete" data-id="' + course.id + '" title="删除" aria-label="删除">×</button></div>' +
      '</article>';
  }

  function renderStudents() {
    const map = new Map();
    courses.forEach(course => {
      const entry = map.get(course.student) || { name:course.student, count:0, hours:0, upcoming:0, last:null };
      entry.count += 1;
      entry.hours += durationHours(course);
      if (courseEnd(course) >= new Date()) entry.upcoming += 1;
      if (!entry.last || courseStart(course) > courseStart(entry.last)) entry.last = course;
      map.set(course.student, entry);
    });
    const students = [...map.values()].sort((a,b) => b.count - a.count);
    if (!students.length) {
      $('studentGrid').innerHTML = '<div class="panel list-empty"><strong>还没有学员记录</strong><span>新增课程后，学员会自动出现在这里。</span></div>';
      return;
    }
    $('studentGrid').innerHTML = students.map(student =>
      '<article class="student-card"><div class="student-card-top"><span class="student-initial">' + escapeHtml(student.name.slice(0,1)) + '</span><span class="status-badge ' + (student.upcoming ? 'success' : 'warning') + '">' + (student.upcoming ? student.upcoming + ' 节待上' : '暂无待上') + '</span></div>' +
      '<h3>' + escapeHtml(student.name) + '</h3><p>最近课程：' + escapeHtml(formatRelativeDay(student.last.date)) + ' · ' + escapeHtml(student.last.type) + '</p>' +
      '<div class="student-stats"><div><small>累计课程</small><strong>' + student.count + '</strong></div><div><small>累计课时</small><strong>' + formatHours(student.hours) + 'h</strong></div></div></article>'
    ).join('');
  }

  function renderSettings() {
    $('defaultReminder').value = String(settings.reminderMinutes);
    $('reminderMinutes').value = String(settings.reminderMinutes);
    $('coachName').value = settings.coachName || '';
    $('coachPhone').value = settings.coachPhone || '';
    const granted = 'Notification' in window && Notification.permission === 'granted';
    $('enableNotifications').textContent = granted ? '已开启' : '开启';
    $('enableNotifications').disabled = granted;
    document.querySelector('.notify-dot').style.display = granted ? 'none' : '';
  }

  function renderAll() {
    renderHeader();
    renderDashboard();
    renderCourseList();
    renderStudents();
    renderSettings();
  }

  function switchView(view) {
    document.querySelectorAll('.view').forEach(node => node.classList.toggle('active', node.dataset.view === view));
    document.querySelectorAll('[data-view-link]').forEach(node => node.classList.toggle('active', node.dataset.viewLink === view));
    const titles = { dashboard:greeting() + '，' + (settings.coachName || '教练'), schedule:'课程安排', students:'我的学员', settings:'提醒与偏好' };
    $('pageTitle').textContent = titles[view];
    if (view === 'dashboard') renderHeader();
    window.scrollTo({ top:0, behavior:'smooth' });
  }

  function roundedStart() {
    const date = new Date();
    date.setMinutes(date.getMinutes() < 30 ? 30 : 60, 0, 0);
    return date;
  }

  function openCourseDialog(course = null) {
    $('courseForm').reset();
    $('conflictAlert').hidden = true;
    $('courseId').value = course?.id || '';
    $('dialogTitle').textContent = course ? '编辑课程' : '新增课程';
    $('repeatWeeks').disabled = Boolean(course);
    if (course) {
      $('studentName').value = course.student;
      $('courseDate').value = course.date;
      $('courseType').value = course.type;
      $('startTime').value = course.startTime;
      $('endTime').value = course.endTime;
      $('location').value = course.location;
      $('reminderMinutes').value = String(course.reminderMinutes);
      $('repeatWeeks').value = '0';
      $('isPaid').checked = course.paid;
      $('notes').value = course.notes || '';
    } else {
      const start = roundedStart();
      const end = new Date(start.getTime() + 60 * 60000);
      $('courseDate').value = toDateKey(start);
      $('startTime').value = pad(start.getHours()) + ':' + pad(start.getMinutes());
      $('endTime').value = pad(end.getHours()) + ':' + pad(end.getMinutes());
      $('reminderMinutes').value = String(settings.reminderMinutes);
      $('repeatWeeks').value = '0';
    }
    $('courseDialog').showModal();
    setTimeout(() => $('studentName').focus(), 40);
  }

  function closeCourseDialog() {
    $('courseDialog').close();
  }

  function findConflict(candidate, excludeId = '') {
    return courses.find(existing =>
      existing.id !== excludeId &&
      existing.date === candidate.date &&
      candidate.startTime < existing.endTime &&
      candidate.endTime > existing.startTime
    );
  }

  function handleCourseSubmit(event) {
    event.preventDefault();
    if (!$('courseForm').reportValidity()) return;
    const id = $('courseId').value;
    const base = {
      id: id || uid(),
      student:$('studentName').value.trim(),
      date:$('courseDate').value,
      type:$('courseType').value,
      startTime:$('startTime').value,
      endTime:$('endTime').value,
      location:$('location').value.trim(),
      reminderMinutes:Number($('reminderMinutes').value),
      paid:$('isPaid').checked,
      notes:$('notes').value.trim()
    };
    if (base.endTime <= base.startTime) {
      showConflict('结束时间必须晚于开始时间。');
      return;
    }
    const repeat = id ? 0 : Number($('repeatWeeks').value);
    const candidates = [];
    for (let i = 0; i <= repeat; i++) {
      const candidate = { ...base, id:i === 0 ? base.id : uid(), date:toDateKey(addDays(new Date(base.date + 'T12:00:00'), i * 7)) };
      const conflict = findConflict(candidate, id);
      if (conflict) {
        showConflict(formatRelativeDay(candidate.date) + ' ' + candidate.startTime + ' 与“' + conflict.student + '”的课程重叠。请调整时间后再保存。');
        return;
      }
      candidates.push(candidate);
    }
    if (id) {
      courses = courses.map(course => course.id === id ? base : course);
      showToast('课程已更新');
    } else {
      courses.push(...candidates);
      showToast(candidates.length > 1 ? '已连续安排 ' + candidates.length + ' 节课程' : '课程已添加');
    }
    closeCourseDialog();
    saveCourses();
  }

  function showConflict(message) {
    $('conflictText').textContent = message;
    $('conflictAlert').hidden = false;
    $('conflictAlert').scrollIntoView({ behavior:'smooth', block:'nearest' });
  }

  function showToast(message) {
    const toast = $('toast');
    toast.querySelector('p').textContent = message;
    toast.classList.add('show');
    clearTimeout(showToast.timer);
    showToast.timer = setTimeout(() => toast.classList.remove('show'), 2600);
  }

  function confirmAction(message) {
    $('confirmMessage').textContent = message;
    $('confirmLayer').hidden = false;
    return new Promise(resolve => { pendingConfirm = resolve; });
  }

  function resolveConfirm(value) {
    $('confirmLayer').hidden = true;
    if (pendingConfirm) pendingConfirm(value);
    pendingConfirm = null;
  }

  function icsEscape(value) {
    return String(value || '').replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');
  }

  function icsLocal(date) {
    return '' + date.getFullYear() + pad(date.getMonth()+1) + pad(date.getDate()) + 'T' + pad(date.getHours()) + pad(date.getMinutes()) + '00';
  }

  function icsUtc(date) {
    return date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
  }

  function courseToEvent(course) {
    const descriptionParts = ['课程类型：' + course.type, course.notes ? '备注：' + course.notes : '', settings.coachName ? '教练：' + settings.coachName : '', settings.coachPhone ? '联系电话：' + settings.coachPhone : ''].filter(Boolean);
    const trigger = Number(course.reminderMinutes) === 1440 ? '-P1D' : '-PT' + Number(course.reminderMinutes) + 'M';
    return [
      'BEGIN:VEVENT',
      'UID:' + course.id + '@ace-coach',
      'DTSTAMP:' + icsUtc(new Date()),
      'DTSTART:' + icsLocal(courseStart(course)),
      'DTEND:' + icsLocal(courseEnd(course)),
      'SUMMARY:' + icsEscape('网球课 · ' + course.student),
      'LOCATION:' + icsEscape(course.location),
      'DESCRIPTION:' + icsEscape(descriptionParts.join('\n')),
      'BEGIN:VALARM',
      'ACTION:DISPLAY',
      'DESCRIPTION:' + icsEscape('网球课提醒：' + course.student),
      'TRIGGER:' + trigger,
      'END:VALARM',
      'END:VEVENT'
    ].join('\r\n');
  }

  function exportCalendar(selected, filename = 'Ace网球课表.ics') {
    if (!selected.length) { showToast('没有可导出的课程'); return; }
    const calendar = ['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Ace Coach Planner//CN','CALSCALE:GREGORIAN','METHOD:PUBLISH','X-WR-CALNAME:Ace 网球课表', ...selected.map(courseToEvent), 'END:VCALENDAR'].join('\r\n');
    const blob = new Blob(['\ufeff' + calendar], { type:'text/calendar;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1500);
    showToast(selected.length === 1 ? '日历文件已生成，请在手机中打开' : '全部课程已导出，可导入手机日历');
  }

  async function enableNotifications() {
    if (!('Notification' in window)) { showToast('当前浏览器不支持网页提醒，请使用手机日历提醒'); return; }
    const result = await Notification.requestPermission();
    renderSettings();
    if (result === 'granted') {
      new Notification('Ace 课表提醒已开启', { body:'课程到点前会在此设备提醒你。手机日历提醒仍然更可靠。', icon:'icon.svg' });
      showToast('浏览器提醒已开启');
    } else {
      showToast('未获得提醒权限，可继续使用手机日历提醒');
    }
  }

  function checkNotifications() {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    const now = new Date();
    let notified = [];
    try { notified = JSON.parse(localStorage.getItem(NOTIFIED_KEY) || '[]'); } catch {}
    courses.forEach(course => {
      const start = courseStart(course);
      const reminderAt = new Date(start.getTime() - Number(course.reminderMinutes) * 60000);
      const token = course.id + '-' + course.date;
      if (now >= reminderAt && now < start && !notified.includes(token)) {
        new Notification('还有' + reminderText(course.reminderMinutes) + '上课', { body:course.student + ' · ' + course.startTime + ' · ' + course.location, icon:'icon.svg', tag:token });
        notified.push(token);
      }
    });
    if (notified.length > 100) notified = notified.slice(-100);
    localStorage.setItem(NOTIFIED_KEY, JSON.stringify(notified));
  }

  function reminderText(minutes) {
    const value = Number(minutes);
    if (value === 1440) return '1 天';
    if (value >= 60 && value % 60 === 0) return (value/60) + ' 小时';
    return value + ' 分钟';
  }

  document.addEventListener('click', async event => {
    const viewLink = event.target.closest('[data-view-link]');
    if (viewLink) { event.preventDefault(); switchView(viewLink.dataset.viewLink); return; }
    if (event.target.closest('#openAddCourse, .add-course-secondary')) { openCourseDialog(); return; }
    const actionButton = event.target.closest('[data-action]');
    if (actionButton) {
      const course = courses.find(item => item.id === actionButton.dataset.id);
      if (!course) return;
      if (actionButton.dataset.action === 'calendar') exportCalendar([course], '网球课-' + course.student + '-' + course.date + '.ics');
      if (actionButton.dataset.action === 'edit') openCourseDialog(course);
      if (actionButton.dataset.action === 'delete') {
        if (await confirmAction('确定删除“' + course.student + '”在 ' + formatRelativeDay(course.date) + ' 的课程吗？')) {
          courses = courses.filter(item => item.id !== course.id);
          saveCourses();
          showToast('课程已删除');
        }
      }
    }
  });

  document.querySelectorAll('[data-schedule-filter]').forEach(button => button.addEventListener('click', () => {
    scheduleFilter = button.dataset.scheduleFilter;
    document.querySelectorAll('[data-schedule-filter]').forEach(node => node.classList.toggle('active', node === button));
    renderCourseList();
  }));
  $('courseSearch').addEventListener('input', renderCourseList);
  $('courseForm').addEventListener('submit', handleCourseSubmit);
  $('closeDialog').addEventListener('click', closeCourseDialog);
  $('cancelDialog').addEventListener('click', closeCourseDialog);
  $('nextCalendarButton').addEventListener('click', () => nextCourse && exportCalendar([nextCourse], '下一节网球课.ics'));
  $('exportAllButton').addEventListener('click', () => exportCalendar(sortCourses(courses.filter(course => courseEnd(course) >= new Date()))));
  $('notificationButton').addEventListener('click', enableNotifications);
  $('enableNotifications').addEventListener('click', enableNotifications);
  $('profileButton').addEventListener('click', () => switchView('settings'));
  $('defaultReminder').addEventListener('change', event => {
    settings.reminderMinutes = Number(event.target.value);
    saveSettingsData();
    $('reminderMinutes').value = event.target.value;
    showToast('默认提醒已更新');
  });
  $('saveSettings').addEventListener('click', () => {
    settings.coachName = $('coachName').value.trim();
    settings.coachPhone = $('coachPhone').value.trim();
    saveSettingsData();
    renderAll();
    showToast('教练资料已保存');
  });
  $('clearDataButton').addEventListener('click', async () => {
    if (await confirmAction('这会删除当前设备上的全部课程，且无法恢复。确定继续吗？')) {
      courses = [];
      saveCourses();
      showToast('全部课程已清空');
    }
  });
  $('confirmCancel').addEventListener('click', () => resolveConfirm(false));
  $('confirmOk').addEventListener('click', () => resolveConfirm(true));
  $('courseDialog').addEventListener('click', event => {
    if (event.target === $('courseDialog')) closeCourseDialog();
  });

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js').catch(() => {}));
  }
  renderAll();
  checkNotifications();
  setInterval(() => { updateCountdown(); checkNotifications(); }, 30000);
})();
