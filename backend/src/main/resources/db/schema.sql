-- 实验室设备预约平台 · 表结构（可重复执行）
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL COMMENT '登录名',
  password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 口令哈希',
  real_name   VARCHAR(50)  DEFAULT NULL COMMENT '姓名',
  role        VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' COMMENT 'ADMIN/TEACHER/STUDENT',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS device_category (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  code        VARCHAR(30)  NOT NULL COMMENT '分类编码',
  name        VARCHAR(50)  NOT NULL COMMENT '分类名称',
  remark      VARCHAR(200) DEFAULT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_code (code),
  UNIQUE KEY uk_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备分类';

CREATE TABLE IF NOT EXISTS device (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  code         VARCHAR(30)  NOT NULL COMMENT '设备编号',
  name         VARCHAR(80)  NOT NULL COMMENT '设备名称',
  category_id  BIGINT       NOT NULL COMMENT '所属分类',
  model        VARCHAR(80)  DEFAULT NULL COMMENT '型号',
  location     VARCHAR(100) DEFAULT NULL COMMENT '存放位置',
  status       VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/MAINTENANCE/RETIRED',
  description  VARCHAR(300) DEFAULT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_code (code),
  KEY idx_device_category (category_id),
  KEY idx_device_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备台账';

CREATE TABLE IF NOT EXISTS time_slot (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(30) NOT NULL COMMENT '如 08:00-10:00',
  start_time TIME        NOT NULL,
  end_time   TIME        NOT NULL,
  sort_no    INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_slot_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约时间段';

CREATE TABLE IF NOT EXISTS booking (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL COMMENT '预约人',
  device_id      BIGINT       NOT NULL,
  slot_id        BIGINT       NOT NULL,
  booking_date   DATE         NOT NULL,
  purpose        VARCHAR(200) DEFAULT NULL COMMENT '用途',
  status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED/COMPLETED/VIOLATED',
  active_key     VARCHAR(120) DEFAULT NULL COMMENT '有效预约唯一键：deviceId_date_slotId，取消/驳回后置空',
  approver_id    BIGINT       DEFAULT NULL,
  approve_remark VARCHAR(200) DEFAULT NULL,
  approved_at    DATETIME     DEFAULT NULL,
  checkin_at     DATETIME     DEFAULT NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_booking_active (active_key),
  KEY idx_booking_user (user_id),
  KEY idx_booking_device_date (device_id, booking_date),
  KEY idx_booking_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备预约';
