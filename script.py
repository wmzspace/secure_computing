import requests
from concurrent.futures import ThreadPoolExecutor
import time
from tqdm import tqdm  # 用于显示进度条
from threading import Lock
from colorama import Fore, Style  # 用于输出带颜色的文本

# 配置目标服务器的 URL
URL = "http://localhost:8080"  # 替换为你的服务器地址

# 每秒发送的请求数量
REQUESTS_PER_SECOND = 100

# 持续时间（秒）
DURATION_SECONDS = 10

# 全局计数器
success_count = 0
failure_count = 0
lock = Lock()  # 用于线程安全地更新计数器


# 定义发送请求的函数
def send_request(request_id):
    global success_count, failure_count
    try:
        response = requests.get(URL)
        with lock:
            success_count += 1
        return f"Request {request_id}: {response.status_code} - {response.text[:50]}"
    except requests.exceptions.RequestException as e:
        with lock:
            failure_count += 1
        return f"Request {request_id}: Failed - {e}"


# 主函数
def main():
    global success_count, failure_count

    print(f"Sending requests to {URL} for {DURATION_SECONDS} seconds...")

    # 使用 ThreadPoolExecutor 实现多线程并发请求
    with ThreadPoolExecutor(max_workers=REQUESTS_PER_SECOND) as executor:
        start_time = time.time()
        end_time = start_time + DURATION_SECONDS
        request_id = 0

        # 初始化进度条
        with tqdm(total=DURATION_SECONDS * REQUESTS_PER_SECOND, desc="Sending Requests", ncols=100) as pbar:
            while time.time() < end_time:
                # 计算剩余时间和需要发送的请求数量
                elapsed_time = time.time() - start_time
                expected_requests = int(elapsed_time * REQUESTS_PER_SECOND)
                to_send = expected_requests - request_id

                # 提交请求任务
                futures = [executor.submit(
                    send_request, request_id + i) for i in range(to_send)]
                request_id += to_send

                # 更新进度条
                for future in futures:
                    future.result()  # 确保任务完成
                    pbar.update(1)

                # 实时显示统计数据（覆盖同一行）
                with lock:
                    success_text = f"{Fore.GREEN}Success: {success_count}{Style.RESET_ALL}"
                    failure_text = f"{Fore.RED}Failed: {failure_count}{Style.RESET_ALL}"
                    tqdm.write(f"{success_text} | {failure_text}")

                # 短暂休眠以避免过度占用 CPU
                time.sleep(1)

    # 确保所有任务完成
    executor.shutdown(wait=True)

    # 输出结果
    print("\n" + "=" * 50)
    print(f"Total Requests Sent: {DURATION_SECONDS * REQUESTS_PER_SECOND}")
    print(f"{Fore.GREEN}Successful Requests: {success_count}{Style.RESET_ALL}")
    print(f"{Fore.RED}Failed Requests: {failure_count}{Style.RESET_ALL}")
    print("=" * 50)


if __name__ == "__main__":
    main()
